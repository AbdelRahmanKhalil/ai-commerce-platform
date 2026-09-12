# 005. Hosted storefront first for MVP

Status: Accepted

## Context

The long-term product supports two merchant paths: a hosted online storefront, and
integration with an existing commerce stack. Following the tenancy/Store model (see
[ADR 002](./002-tenancy-and-store-model.md)), these are two possible Channels of the
same kind of Store, not two Store types. Building both paths fully in MVP would dilute
effort across two large surfaces without validating either deeply, and the external-
integration path in particular can't be well-designed without a real target provider's
actual data model, auth scheme, and constraints.

## Decision

- **MVP proves the hosted-web channel end-to-end** as a real, sellable product: catalog,
  inventory, cart/checkout, Cash-on-Delivery order lifecycle, and a storefront AI
  shopping assistant, all reachable at a Store's subdomain.
- **The backend `storefront` module does not render HTML.** It owns public Store
  resolution (by hostname, fail-closed) and the public storefront query/command APIs. A
  separate **Next.js application renders the customer-facing storefront**, calling those
  APIs. This is a frontend/backend split, not a backend service extraction — the backend
  remains one deployable modular monolith (see
  [ADR 001](./001-modular-monolith-and-spring-modulith.md)).
- **External-commerce integration stays a documented future bounded context, not
  built.** No generic Integration API, no speculative sync/ingestion ports, and no
  `integration` module exist yet (see
  [architecture.md](../proposals/architecture.md#13-external-integration-seam)). It is
  designed and built only once a first real integration target is selected.
- **No generic Channel abstraction is built in MVP.** A Store has exactly one implicit
  channel (hosted web); the real `Channel`/`Connection` entity is introduced only when a
  second channel is actually being integrated.

## Alternatives considered

(Carried over from [product-mvp.md](../proposals/product-mvp.md#1-exact-mvp-product-scope):)

- **Integration-first MVP** — build the external-commerce integration and
  conversations/AI layer first, skip the hosted storefront. Rejected: produces a more
  reusable core for established merchants but no self-serve product a small merchant can
  sign up and sell through immediately, and the channel work (WhatsApp/Instagram) needed
  for a real omnichannel story is itself deferred either way.
- **Storefront-only, no AI in MVP** — ship catalog/inventory/orders/storefront without
  any AI capability, add AI later. Rejected: faster to ship but risks looking like
  generic e-commerce software rather than the AI-differentiated platform this product is
  meant to be, and delays learning whether the tool-calling/RAG boundary design holds up
  under a real feature.
- **Both paths thin** — a shallow slice of both hosted storefront and external
  integration. Rejected: spreads effort across two surfaces instead of validating one
  deeply, raising the risk that neither is production-quality at MVP.

## Consequences

- The hosted-web channel gets full engineering attention and can become a genuinely
  sellable product; the external-integration and multi-channel story is not
  demonstrable until later milestones.
- The Next.js frontend and the Spring Modulith backend are separate deployables from day
  one (a frontend/backend split), which is a minimal, ordinary web-application topology
  — not the kind of premature infrastructure `CLAUDE.md` warns against, since it doesn't
  introduce a distributed backend architecture.
- When a first real integration is selected, its interfaces will be designed against
  that provider's actual requirements rather than a speculative generic shape — at the
  cost of that work not starting until a provider is actually chosen.

## What would cause us to revisit this decision

- A first real external-integration target (e.g. Shopify) is selected — triggers
  designing and building the `integration` module and its concrete interfaces.
- A merchant commits to a second channel (an external site connection, or a messaging
  channel like WhatsApp/Instagram) — triggers building the real `Channel`/`Connection`
  entity.
- Evidence from real merchant demand that the storefront-first bet was wrong — e.g.
  prospective customers overwhelmingly need integration with an existing stack rather
  than a new hosted storefront.
