# System Architecture Proposal

Status: Proposal — pending review. No code/infrastructure is implemented from this yet.
Revised to reflect decisions from the 2026-09-11 architecture review.

Related: [domain-model.md](./domain-model.md) · [security-and-tenancy.md](./security-and-tenancy.md) ·
[ai-architecture.md](./ai-architecture.md) · [evolution-roadmap.md](./evolution-roadmap.md)

## Modular monolith module boundaries

One deployable, package-by-feature, with modules mirroring the domain concepts in
[domain-model.md](./domain-model.md) so that a future service extraction (see
[evolution-roadmap.md](./evolution-roadmap.md#19-likely-future-service-extraction-boundaries))
follows existing seams rather than inventing new ones:

- `tenancy` — UserAccount (our local user, mapped to the Keycloak JWT subject),
  Organization, Membership, Store, StoreAccess, and tenant/store authorization. Keycloak
  itself remains the external identity provider — this module owns the application-side
  tenancy and authorization model, not identity proofing (see
  [security-and-tenancy.md](./security-and-tenancy.md#11-authentication-approach)).
- `catalog` — Product, ProductVariant, Category.
- `inventory` — InventoryItem, InventoryReservation.
- `ordering` — Cart, Order, OrderStatus/PaymentStatus/FulfillmentStatus, Payment.
- `customers` — Customer, ChannelIdentity, AnonymousStorefrontSession.
- `storefront` — does not render HTML. Owns public Store resolution by hostname and the
  public storefront query/command APIs; the customer-facing storefront itself is
  rendered by a separate Next.js application that calls these APIs. This is a
  frontend/backend split, not a backend service extraction — the backend remains one
  deployable.
- `conversations` — Conversation, Message (Web channel only in MVP; no generic Channel
  entity yet, see [domain-model.md](./domain-model.md#4-tenant--organization--merchant--store-relationships)).
- `ai` — RAG over unstructured knowledge, structured tool-calling gateway, AI audit log
  (see [ai-architecture.md](./ai-architecture.md)).

`integration` is deliberately **not** in the initial module list — see
[Section 13](#13-external-integration-seam): it is a documented future bounded context,
introduced only once a first real external integration is selected.

**Decided: adopt Spring Modulith** to make these module boundaries mechanically
enforced rather than convention-only — module dependencies are verified at build/test
time, and Spring Modulith's documentation/event-publication support gives a natural
place to introduce in-process domain events later without pulling in a broker
prematurely. Per CLAUDE.md, modules must not reach into another module's
repositories/internals — cross-module calls go through each module's public application
service. This is the mechanical enforcement of the tenant-isolation and AI-authorization
invariants: if the `ai` module cannot reach `ordering`'s repository directly, it cannot
bypass `ordering`'s authorization checks either.

## 13. External integration seam

Established merchants need to eventually connect an existing commerce stack. The
2026-09-11 decision is to **keep this documented as an architectural concept, and
explicitly not build it yet**:

- No generic Integration API is built in MVP.
- No speculative `ProductSyncPort` / `InventoryUpdatePort` / `OrderIngestionPort`
  interfaces are defined now. Designing these before a real integration exists risks
  shaping them around imagined requirements rather than a real provider's actual data
  model and constraints (webhooks vs. polling, auth scheme, rate limits, field mappings).
- **When the first real integration is selected** (e.g. Shopify, or another provider),
  its concrete interfaces are defined then, informed by that provider's real
  requirements, and an `integration` module is introduced at that point to receive them
  without disturbing `catalog`/`ordering`/`inventory`. It is not created ahead of time.
- The domain model change in this revision — a Store as a brand that can connect to
  multiple Channels (see [domain-model.md](./domain-model.md#4-tenant--organization--merchant--store-relationships)) —
  is what makes an eventual external-site or external-connector integration a Channel
  on an existing Store, not a different kind of Store. That's the one architectural
  commitment made now; everything else about the integration is left until a real
  provider is chosen.

## 16. Which operations should remain synchronous

Recommendation: **everything in MVP is synchronous request/response.** Concretely:

- Catalog/inventory CRUD, cart/checkout, order placement, auth — ordinary synchronous
  calls within a single transaction where correctness requires it (e.g., InventoryReservation
  consumption + order creation + Payment record, see [domain-model.md](./domain-model.md#10-initial-order-lifecycle)).
- The AI shopping assistant is a synchronous chat request/response from the storefront's
  point of view, even though it internally calls an LLM, a small set of structured tools,
  and (for unstructured questions) a retrieval store — no message queue between the
  widget and the assistant in MVP.
- **Payment, MVP:** Cash on Delivery / manual payment recording means there is **no
  external payment gateway call at all** in the order-placement path — order + inventory
  + Payment record is entirely internal to one Postgres transaction, with no network call
  to any third party in the critical path. When an online gateway is introduced later
  (see [evolution-roadmap.md](./evolution-roadmap.md#payments--shipping)), it will need
  an idempotency-key-and-webhook-reconciliation pattern like any external payment
  integration — but that's a future design point, not something MVP needs to build or
  even stub out now.

**Why:** everything above lives in one deployable with one Postgres database. A single
ACID transaction already gives correctness guarantees that a message broker would only
approximate (with added failure modes: ordering, redelivery, eventual consistency). Per
CLAUDE.md, synchronous is the default until a concrete requirement says otherwise —
nothing in MVP scope crosses an independent transactional boundary yet, and removing the
payment gateway from MVP (decision 8) removes the one external call that would have been
in the critical path.

## 17. Which future requirements would genuinely justify events/Kafka

Not "will we eventually have events" — Kafka specifically is justified when there are
**independently deployed/scaled consumers** that need durable, replayable delivery. Concrete
future triggers:

- **Service extraction has happened** (see [Section 19](#19-likely-future-service-extraction-boundaries))
  and, e.g., an independently deployed Inventory service and Analytics service both need
  to react to `OrderPlaced` without synchronous coupling to Ordering.
- **High fan-out on one event**: `OrderPlaced` needing to notify analytics, email/SMS,
  loyalty, a WhatsApp follow-up, and an external merchant's webhook — once the number of
  independent consumers with their own retry/backpressure needs grows past a couple, a
  broker earns its keep over direct synchronous calls fanned out in-process.
- **Durable replay / multiple independent consumer groups / stream processing** — e.g.,
  rebuilding analytics aggregates from history, or a consumer that needs to reprocess a
  historical event stream. This is specifically what distinguishes "we need Kafka" from
  "we need any queue."

**Explicitly not sufficient justification:** channel webhook ingestion at moderate
volume, or any single-consumer "notify one other thing" case — those are served by a
transactional outbox with a simple poller, or even a lighter queue (SQS/RabbitMQ/a
Postgres-backed queue), before Kafka's operational cost (cluster ops, schema
registry, consumer-group management) is worth paying. Deferred per decision 14 until one
of the above triggers is actually reached.

## 18. Which future workflows could genuinely justify Saga

Saga is for one business workflow spanning **independently transactional** boundaries
that require compensation on partial failure. MVP has none of these — order placement is
one transaction in one database with no external calls (Cash on Delivery, see
[Section 16](#16-which-operations-should-remain-synchronous)). Future candidates, once the
relevant steps are owned by separately deployed services or external systems:

- **Order fulfillment across online payment capture (external gateway, once introduced,
  see [evolution-roadmap.md](./evolution-roadmap.md#payments--shipping)) + inventory
  reservation (internal) + shipping label purchase (external carrier)** — once
  shipping-carrier integration exists, a failure after payment capture but before label
  purchase needs an explicit compensation (refund) rather than a single rollback.
- **Order sync to an established merchant's own system** with a confirmation round-trip
  and possible partial failure — compensating actions (retry, mark as failed, notify) span
  a boundary we don't transactionally control. Relevant once the first real external
  integration (see [Section 13](#13-external-integration-seam)) is built.
- **Return/refund workflows** spanning payment refund + inventory restock + notification,
  once those are separate services rather than calls within one transaction.

**Not justified for MVP:** all of the above require either an online payment gateway or
an external integration, both explicitly deferred. Deferred per decision 14 until one of
the above triggers is actually reached.

## Related decisions

Section 19 (service extraction boundaries) and the phased triggers above are elaborated
as a roadmap in [evolution-roadmap.md](./evolution-roadmap.md), since they're forward-looking
rather than an MVP architecture decision.
