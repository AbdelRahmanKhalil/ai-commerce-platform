# 002. Tenancy and Store model

Status: Accepted

## Context

This is a multi-tenant SaaS. `CLAUDE.md` requires tenant isolation as a security
invariant and requires every tenant-scoped operation to prove the authenticated
principal has access to that tenant. We also need a model that supports two long-term
merchant paths — a hosted storefront and integration with an existing commerce stack —
without those paths implying two different data shapes, and without over-building
speculative structure (a generic Channel abstraction, cross-Store customer merging,
multi-currency pricing) ahead of a concrete need.

## Decision

- **Organization** is the tenant root and the security/billing boundary.
- **Store** is a merchant brand/commerce operation belonging to an Organization — not a
  sales channel. A Store owns one catalog and will eventually connect to multiple
  Channels (hosted web, an external site, WhatsApp, Instagram); MVP hardcodes exactly
  one implicit channel (hosted web) per Store and does not build a `Channel` table until
  a second channel is actually being integrated.
- **`tenancy` module** owns `UserAccount` (our local user, mapped to the Keycloak JWT
  subject), `Organization`, `Membership`, `Store`, `StoreAccess`, and tenant/store
  authorization. Keycloak remains the external identity provider only (see
  [ADR 003](./003-keycloak-identity-and-application-authorization.md)).
- **`OrganizationRole`** (on `Membership`): `OWNER`, `ADMIN`, `MEMBER`. `OWNER` and
  `ADMIN` have Organization-wide access to every Store. `MEMBER` is staff who belongs to
  the Organization but gains Store-level business access only through `StoreAccess`.
- **Store-scoped role** (on `StoreAccess`): `MANAGER`, `CATALOG_EDITOR`,
  `ORDER_MANAGER`, `SUPPORT_AGENT`. `StoreAccess` is included from day one, not deferred
  until a multi-Store Organization needs it.
- **One currency per Store in MVP**: Store carries an ISO-4217 `currencyCode` (`EGP`
  initially); monetary amounts use decimal/`BigDecimal`, never floating-point;
  multi-currency pricing is deferred as a future migration.
- **Customer identity is Store-scoped and independent across Stores in MVP** — no
  speculative cross-Store customer merge or global-customer entity is modeled ahead of a
  concrete case requiring it.
- A `UserAccount` may hold `Membership` in multiple Organizations; an agency managing
  genuinely separate legal entities is modeled as separate Organizations tied together
  by that multi-Organization membership, not by an extra hierarchy level above Store.

## Alternatives considered

- **Store as a sales channel type (`HOSTED` vs. `EXTERNAL`)** — the original model.
  Rejected: it would require a type migration the moment a merchant who started hosted
  also wants to connect an existing site or add WhatsApp, when that's naturally just an
  additional Channel on the same Store.
- **`Organization → Merchant → Store` three-level hierarchy** for agencies/multi-brand
  businesses. Rejected: Store already fills the role that extra layer would have
  played; a genuinely separate legal entity is better modeled as its own Organization.
- **Defer `StoreAccess` until a multi-Store Organization needs it.** Rejected: the cost
  of including it from day one is low, and retrofitting a narrower access model onto an
  authorization system already in use is materially riskier than building it in from
  the start.
- **Global customer identity across Stores from day one.** Rejected: would require
  deciding a cross-brand identity-merge policy before there is a concrete case that
  needs it; Store-scoped identity with merging as explicit future work avoids that
  premature design.
- **Generalized multi-currency Price schema now.** Rejected: no concrete near-term
  multi-currency requirement exists; a single `currencyCode` per Store is simpler and a
  future multi-currency need is treated as an accepted migration, not a cost paid today.

## Consequences

- Onboarding a merchant to a second channel or a second Store never requires a schema
  migration for the underlying Store/Organization model — only new rows.
- Every tenant-scoped repository/service call must carry explicit
  `organizationId`/`storeId` parameters (see
  [ADR 004](./004-postgresql-and-application-level-tenant-isolation.md)) — there is no
  implicit "current tenant."
- `MEMBER` staff with no `StoreAccess` grants have no Store access at all — this must be
  covered by cross-tenant authorization tests from the first PR that touches
  tenant-scoped data, per `CLAUDE.md`.
- Multi-currency, cross-Store customer merging, and the generic Channel abstraction are
  all deferred; each will require a distinct future design and, in most cases, a
  migration rather than an extension of existing structure.

## What would cause us to revisit this decision

- A merchant commits to a second channel on an existing Store — triggers building the
  real `Channel`/`Connection` entity (see
  [evolution-roadmap.md](../proposals/evolution-roadmap.md#channels-and-the-generic-channel-abstraction)).
- Real multi-currency demand — triggers generalizing the Price schema and accepting the
  associated migration.
- A concrete need for an Organization to recognize the same shopper across two of its
  Stores — triggers designing cross-Store customer identity merging.
- Evidence that most Organizations run multiple Stores from the start, making the
  Store-scoped `StoreAccess` model (rather than Organization-wide roles) the common
  case rather than the exception — would not change this decision but would reprioritize
  admin-console UX around it.
