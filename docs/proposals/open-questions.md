# Open Questions & Risks

Status: Proposal — pending review.
Revised to reflect decisions from the 2026-09-11 architecture review — resolved items
removed, remaining and new items retained below.

Related: all other docs in this directory. This file collects (a) decisions elsewhere
that were deliberately left as an explicit choice rather than defaulted, and (b) risks
not yet covered by any proposal.

## Resolved by the 2026-09-11 review (no longer open)

For traceability: payment-gateway-in-MVP-or-not is resolved (COD/manual only, gateway
deferred — decision 8); the auth provider choice is resolved (Keycloak — decision 9);
application-layer vs. RLS tenant isolation is resolved (application-layer for MVP,
decision 10); whether to build a generic Integration API in MVP is resolved (no —
decision 12); and the "Organization → Merchant → Store" layering question is resolved by
redefining Store as the brand-level entity itself (decision 3) — an agency managing
distinct legal entities is still modeled as separate Organizations tied by multi-org
Membership, not a new hierarchy level.

## Resolved by the final architecture-review corrections (no longer open)

- **Multi-currency intent / Price schema generalization** (was #1): resolved — Store
  carries a single ISO-4217 `currencyCode` (EGP initially), amounts are decimal, and
  multi-currency is deferred as a future migration rather than generalized now (see
  [domain-model.md](./domain-model.md#6-catalog-product-and-product-variant-modeling)).
- **Store-scoped staff access (StoreAccess) granularity** (was #2): resolved —
  `StoreAccess` ships from day one, not deferred (see
  [security-and-tenancy.md](./security-and-tenancy.md#authorization-model)).
- **Cross-Store customer identity merging** (was #3): resolved — Customer identity
  remains completely independent per Store in MVP; no speculative global-customer/merge
  entity is reserved in the schema (see
  [domain-model.md](./domain-model.md#8-customer-identity-model)).
- **COD operational edge cases** (was #6): resolved as explicitly out of scope for MVP —
  partial payment and mid-flow payment-method switching are not supported; more advanced
  COD edge cases are deferred future work, not merely undesigned (see
  [domain-model.md](./domain-model.md#10-initial-order-lifecycle)).
- **Keycloak realm/client topology** (was #9): resolved — one shared platform realm for
  all Organizations' staff, not per-Organization realms (see
  [security-and-tenancy.md](./security-and-tenancy.md#11-authentication-approach)).
- **Membership/StoreAccess lookup performance / caching** (was #10): resolved —
  authorization checks are not cached initially; PostgreSQL is queried directly on every
  request and Redis is not introduced for this (see
  [security-and-tenancy.md](./security-and-tenancy.md#authorization-checks-are-not-cached)).

## Decisions that need your sign-off, not just engineering judgment

1. **Customer authentication path, when built.** Decision 9 defers customer
   authentication entirely. When it's introduced, should it go through Keycloak
   (consistent with staff auth, but Keycloak's B2B-oriented realm/group model may not
   fit lightweight B2C accounts well) or a separate, simpler mechanism? Not urgent for
   MVP since guest usage is valid, but worth deciding before customer accounts are
   designed rather than during.

## New questions introduced by the 2026-09-11 decisions

2. **Generic Channel abstraction timing and shape.** [evolution-roadmap.md](./evolution-roadmap.md#channels-and-the-generic-channel-abstraction)
   defers building a real `Channel`/`Connection` entity until a second channel is
   actually being integrated. Should MVP's hosted-web presence be modeled as an implicit
   default (as currently proposed) or as a minimal, explicit `Channel` row from the
   start, to make that later migration additive rather than a schema change touching
   every Store? Currently proposed as implicit; worth confirming that's acceptable.
3. **AI audit redaction policy specifics.** [ai-architecture.md](./ai-architecture.md#ai-auditing-metadata-and-redaction-not-raw-content-by-default)
   establishes that raw AI tool inputs/outputs aren't persisted by default, but the exact
   metadata schema and what (if anything) qualifies for short-retention raw capture
   for debugging is not yet designed. Needs a concrete policy before the AI audit log is
   built, not just the principle established here.
4. **AnonymousStorefrontSession lifecycle.** How long does a guest session live, how does
   it map to a Cart across visits/devices, and what happens to its history if the
   shopper later registers as a Customer? Not designed at proposal level.

## Risks and gaps not yet addressed by any proposal

- **Data residency / compliance (GDPR and similar)** for customer PII across regions —
  not addressed by any doc here. Matters more once Customer data crosses borders or the
  business targets EU merchants/customers specifically.
- **Rate limiting and abuse protection**, especially for the public storefront and the AI
  chat endpoint. The AI endpoint carries a direct cost-per-call (LLM tokens) in a way
  ordinary CRUD endpoints don't — an abusive or scripted guest (now explicitly a
  supported unauthenticated actor via `AnonymousStorefrontSession`) could generate real
  operating cost, not just load. No design proposed yet; this is arguably more urgent now
  that guest AI usage is an explicit MVP feature rather than an assumption.
- **LLM cost/usage metering per tenant.** Related to the above: if AI usage has a
  marginal cost, the pricing/billing model likely needs per-tenant (per-Store, given
  Store-scoped knowledge bases) usage tracking from early on, even if billing enforcement
  comes later.
- **Product search/filtering at scale.** Not scoped in any doc. Postgres full-text search
  is likely sufficient for MVP catalog sizes; flagged so it isn't silently assumed away.
- **Webhook signature verification and replay protection** for future channel providers
  (WhatsApp/Instagram, external-site connectors) and any future payment gateway
  callback — mentioned in [evolution-roadmap.md](./evolution-roadmap.md#channels-and-the-generic-channel-abstraction)
  but not designed. This is a security boundary and should be designed before the first
  webhook consumer is built, not retrofitted.
- **Noisy-neighbor risk within the monolith.** Even a single-tenant-per-request model
  doesn't prevent one Organization's heavy catalog/AI usage from degrading others sharing
  the same process/database. No per-tenant resource limiting (connection pool shares,
  per-tenant rate limits) is proposed yet.
- **Tenant data export and deletion** ("right to be forgotten", account offboarding) —
  not addressed. Worth deciding early whether deletion is a hard requirement, since it
  affects whether any table can ever hard-delete tenant-scoped rows or must support
  tombstoning/anonymization instead.
- **Backup/disaster recovery** — not addressed; out of scope for an architecture proposal
  but should be tracked as a pre-launch requirement.
- **Testing strategy for cross-tenant isolation and AI-tool authorization.** CLAUDE.md
  requires automated tests for both (restated as mandatory in decision 10), but no doc
  here specifies *how* (e.g., a shared test harness that attempts cross-tenant/cross-Store
  access against every tenant-scoped endpoint as a matter of course, vs. ad hoc tests per
  feature). Worth deciding as a convention before the first tenant-scoped module is
  built, so coverage doesn't depend on each implementer remembering to write these tests
  independently.
