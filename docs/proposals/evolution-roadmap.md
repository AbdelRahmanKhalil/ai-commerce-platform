# Evolution Roadmap Proposal

Status: Proposal — pending review. This is a forward-looking map, not a build plan.
Revised to reflect decisions from the 2026-09-11 architecture review.

Related: [architecture.md](./architecture.md) · [domain-model.md](./domain-model.md) ·
[product-mvp.md](./product-mvp.md) · [open-questions.md](./open-questions.md)

Purpose: the long-term capability list in the brief (hosted storefronts, external
integrations, WhatsApp/Instagram, AI agents, RAG, payments, shipping, Kafka, outbox,
Saga, Redis, Docker, Kubernetes, observability) is real, but per CLAUDE.md none of it
should be introduced until a concrete requirement justifies it — and when it is
introduced, the reason must be documented. This doc is that documentation, written in
advance as trigger conditions rather than a fixed timeline.

## 19. Likely future service extraction boundaries

The modular monolith's module boundaries (see [architecture.md](./architecture.md)),
mechanically enforced via **Spring Modulith** (decided), are chosen so extraction later
follows existing seams:

| Future service | Extraction trigger |
|---|---|
| **Catalog & Inventory** | Read volume or caching/scaling needs diverge significantly from the rest of the system (product browsing is typically the highest-QPS path). |
| **Order & Payments** | Need for an independent compliance/security boundary (e.g. PCI scope isolation, relevant once an online gateway exists) or independent scaling of the transactional core. |
| **Conversations/Channels** | Once WhatsApp/Instagram or a first external-site Channel exist: webhook-driven I/O with a very different load/scaling profile than the rest of the system. |
| **AI/RAG** | Distinct scaling profile (LLM/embedding workload), possibly a separate team, and a natural place to isolate provider-specific dependencies. |
| **Tenancy** | Keycloak is already a separate process; extract the application-side tenancy/authorization logic only if multiple products/services need to share the same Membership/StoreAccess data directly. |
| **Integration/Connectors** | Once real external-commerce connectors (Shopify, WooCommerce, ...) are built, each is naturally its own deployable as the number of connectors grows. |

None of these are triggered by MVP scope. Extracting before a real trigger repeats the
mistake CLAUDE.md explicitly warns against ("do not introduce microservices merely to
demonstrate microservices").

## Channels and the generic Channel abstraction

**Trigger:** a merchant commits to a second channel for an existing Store — a first
external-site connection, or a first messaging channel (WhatsApp/Instagram). Before this:

- The generic `Channel`/`Connection` entity described conceptually in
  [domain-model.md](./domain-model.md#4-tenant--organization--merchant--store-relationships)
  is designed and built for real at that point — not before, per the 2026-09-11 decision
  not to over-design it while a Store has only one (implicit, hardcoded) hosted-web
  channel.
- Webhook signature verification and replay protection must be designed (see
  [open-questions.md](./open-questions.md) — currently undesigned) before any inbound
  webhook-driven channel (WhatsApp/Instagram, or a webhook-based external-site
  connector) is built.
- `ChannelIdentity` linking (already modeled, see
  [domain-model.md](./domain-model.md#8-customer-identity-model)) becomes load-bearing:
  this is where, e.g., a WhatsApp phone number gets tied to an existing Store-scoped
  Customer record.

## External commerce integrations

**Trigger:** an established merchant wants to connect their existing stack as a Channel
on one of their Stores. Per the 2026-09-11 decision, **no generic Integration API or
speculative ports (`ProductSyncPort`, `InventoryUpdatePort`, etc.) are built ahead of
this.** Instead:

- When the first real integration target is selected (Shopify, most likely, given market
  share, or another provider based on actual merchant demand), its concrete interfaces
  are defined then, sized to that provider's real data model, auth scheme (webhooks vs.
  polling), and constraints — not to a guessed-at generic shape.
- An `integration` module is introduced at that point (see
  [architecture.md](./architecture.md#modular-monolith-module-boundaries)) to receive
  this work without disturbing `catalog`/`ordering`/`inventory` — it is not created
  ahead of time.
- Expect the first connector to reveal requirements a speculative interface would have
  gotten wrong — that's the reason this is sequenced after provider selection rather
  than before.

## Payments & shipping

- **Payments:** MVP is Cash on Delivery / manual payment recording only (see
  [domain-model.md](./domain-model.md#10-initial-order-lifecycle)) — no online gateway,
  no assumption of Stripe or any specific provider. **Trigger for introducing an online
  gateway:** real merchant demand for a payment method COD can't serve (digital goods,
  international orders, high-value orders, or a merchant's own fraud/return-rate
  experience with COD). At that point, build a `PaymentGatewayPort`-style provider
  abstraction sized to that first real provider's actual requirements (per decision 8)
  — not scaffolded speculatively now, for the same reason the integration seam above
  isn't. This is also the point where the idempotency-key-plus-webhook-reconciliation
  pattern (see [architecture.md](./architecture.md#16-which-operations-should-remain-synchronous))
  needs to be designed for real.
- **Shipping:** flat-rate/manual in MVP. **Trigger for carrier API integration:** a
  merchant needs real-time rates or label purchase — at that point, Saga-style
  compensation becomes relevant (see [architecture.md](./architecture.md#18-which-future-workflows-could-genuinely-justify-saga))
  because label purchase is an external, independently-transactional step.

## Kafka / transactional outbox

Trigger conditions are detailed in
[architecture.md](./architecture.md#17-which-future-requirements-would-genuinely-justify-eventskafka).
Summary: introduce a transactional outbox first (cheap, no new infrastructure, solves
"a DB transaction must reliably produce an external event"), and only reach for Kafka
once there are multiple independently-deployed consumers needing durable, replayable
delivery — most plausibly once services have actually been extracted per
[Section 19](#19-likely-future-service-extraction-boundaries). Deferred per decision 14
until one of those triggers is actually reached.

## Saga

Trigger conditions are detailed in
[architecture.md](./architecture.md#18-which-future-workflows-could-genuinely-justify-saga).
Summary: relevant once a single business workflow (fulfillment, external order sync,
refunds) spans steps that are no longer inside one database transaction — i.e., after
either an online payment gateway, shipping-carrier integration, or service extraction
exist, none of which MVP includes. Deferred per decision 14 until one of those triggers
is actually reached.

## Redis

**Trigger:** a concrete need for shared cache or distributed session/rate-limit state
across more than one process — e.g., session storage once there's more than one
application instance behind a load balancer, or caching hot catalog reads once Postgres
alone is measurably insufficient. Not needed for a single-instance MVP.

## Docker / Kubernetes / observability

- **Decided: Docker is adopted early**, purely as a local-dev/deployment packaging
  convenience — this is not "infrastructure complexity" in the sense CLAUDE.md warns
  about, since it doesn't change the architecture, only how the one deployable is
  shipped.
- **Decided: Kubernetes is deferred** until there is a meaningful deployment to
  orchestrate — more than a small number of instances, or a need for
  autoscaling/self-healing that a simpler deployment target (a single host, a managed
  container service) doesn't provide. Adopting K8s for a single-instance modular
  monolith would be exactly the premature-infrastructure case CLAUDE.md warns against.
- **Decided: basic structured logging, request correlation (a correlation/trace id
  threaded through a request), security auditing, and AI tool auditing (see
  [ai-architecture.md](./ai-architecture.md#ai-auditing-metadata-and-redaction-not-raw-content-by-default))
  exist early** — not deferred to a "later, full observability" phase. This is narrower
  than full distributed tracing/metrics infrastructure (dashboards, span propagation
  across services), which genuinely has no target yet in a single-deployable MVP and is
  deferred until there's more than one deployable to correlate across. The distinction:
  CLAUDE.md's own testing/security rigor (cross-tenant test evidence, AI action
  auditability) already requires *some* structured logging to be verifiable at all, so
  that minimum ships with MVP rather than waiting for a later infrastructure milestone.
