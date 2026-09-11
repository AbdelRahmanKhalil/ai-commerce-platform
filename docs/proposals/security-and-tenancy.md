# Security & Tenancy Proposal

Status: Proposal — pending review. No auth/authorization code is implemented from this yet.
Revised to reflect decisions from the 2026-09-11 architecture review.

Related: [domain-model.md](./domain-model.md) · [architecture.md](./architecture.md) ·
[ai-architecture.md](./ai-architecture.md) · [open-questions.md](./open-questions.md)

## 11. Authentication approach

**Decided: Keycloak** is the OIDC/OAuth2 identity provider for staff UserAccounts. This
resolves the previously-open choice between a self-hosted IdP, an in-process auth
module, and a third-party IDaaS.

Per the 2026-09-11 decision, Keycloak's role is narrow and deliberate:

- **One shared platform realm, not one realm per Organization.** All Organizations'
  staff authenticate against the same Keycloak realm/client. Keycloak has no concept of
  Organization at all — that boundary exists only in our own `tenancy` module data (see
  [Section 12](#12-tenant-scoped-authorization-approach)). This resolves the
  previously-open realm-topology question (see [open-questions.md](./open-questions.md)).
- **Keycloak proves identity only.** It authenticates a UserAccount and issues a JWT
  whose subject identifies that UserAccount. It does **not** carry Organization
  membership, Store access, or business roles.
- **Organization membership, Store access, and business roles live in our PostgreSQL
  database** (`Membership`, `StoreAccess` — see [domain-model.md](./domain-model.md#4-tenant--organization--merchant--store-relationships)),
  not in Keycloak realms/groups and not in the token. This keeps the tenancy model
  (many-to-many UserAccount↔Organization, optional Store-level narrowing) entirely in
  application data we control, rather than split across an identity provider's group/role
  model and our own schema.
- **The JWT is a proof of who, never a proof of access to what.** Every tenant-scoped
  request still resolves access from `Membership`/`StoreAccess` server-side — see
  [Section 12](#12-tenant-scoped-authorization-approach).
- **Customer authentication is deferred.** Guest storefront usage — browsing, cart,
  checkout, and AI shopping assistance via `AnonymousStorefrontSession` (see
  [domain-model.md](./domain-model.md#8-customer-identity-model)) — is a fully valid MVP
  flow with no customer login at all. When customer accounts are introduced, they may or
  may not go through Keycloak (open question — see
  [open-questions.md](./open-questions.md)); nothing about the storefront depends on that
  choice today.

Access tokens remain short-lived JWTs with refresh tokens for session continuity, per
CLAUDE.md's standards-based authentication requirement.

## 12. Tenant-scoped authorization approach

This is the mechanical expression of CLAUDE.md's tenant-isolation invariant: **"never
trust a tenant/store identifier merely because the client supplied it"** and **"every
tenant-scoped operation must prove that the authenticated principal has access to that
tenant."** The 2026-09-11 decision sharpens this further: since the JWT no longer carries
an organization/role claim to cross-check against, **the `Membership`/`StoreAccess`
lookup is the only source of truth, on every request** — there is no shortcut through the
token.

### Token and requested resource context

- A staff JWT carries only the UserAccount's subject (identity). It carries no
  Organization id, Store id, or role.
- A client (the admin console) requests a specific **Organization**, and optionally a
  **Store**, as part of each request — e.g., a path segment or header. This is treated as
  **requested resource context, not a trusted claim**: the server resolves the
  UserAccount's `Membership` for that Organization (and `StoreAccess` for that Store, if narrower than
  the Organization-level role) and authorizes the request against that, rejecting on any
  mismatch. A user switching which Organization/Store they're working in changes only
  this request-time context, never the token.
- A Customer/guest request on the storefront resolves its Store from the hostname (see
  [domain-model.md](./domain-model.md#9-hosted-storefront-model)) — there is no token to
  check against on that path, so the hostname resolution itself is the trust boundary and
  must fail closed.

### Authorization model

Recommendation: **RBAC with a small, fixed MVP role set**, now split across two levels to
match Store now being a distinct scoping unit under the Organization:

- **Organization-level role** (`OrganizationRole` on `Membership`) — `OWNER`, `ADMIN`,
  `MEMBER`. `OWNER` and `ADMIN` imply access to every Store under the Organization.
  `MEMBER` implies no Store access on its own — a `MEMBER` is staff who belongs to the
  Organization and gains business access only through `StoreAccess` grants.
- **Store-scoped role** (via `StoreAccess`, narrowing a `MEMBER`) — `MANAGER`,
  `CATALOG_EDITOR`, `ORDER_MANAGER`, `SUPPORT_AGENT`. A `MEMBER` with only `StoreAccess`
  grants has no access to Stores they weren't explicitly granted, even within the same
  Organization. **Decided: `StoreAccess` ships from day one**, not deferred until a
  multi-Store Organization needs it — this resolves the previously-open scope question
  (see [open-questions.md](./open-questions.md)).

Both are enforced with method-level checks in each module's application services, backed
by a shared "does this UserAccount have the required Organization role, or StoreAccess
role for this Store" check. Per CLAUDE.md, avoid building a generic, tenant-configurable
permissions engine before a concrete need for it exists — a fixed role set is sufficient
for MVP.

### Explicit scoping convention

Per decision 10, **repository and application-service method signatures must take the
scoping id(s) (`organizationId`, and `storeId` where applicable) as explicit parameters**
— never resolved implicitly from a thread-local "current tenant" inside the repository
itself. This is a concrete, reviewable convention: a query missing its scoping parameter
is visible in the method signature and in code review, not hidden inside an
auto-magically-scoped base repository. It's also what the mandatory cross-tenant tests
(below) are structured to exercise: call an application service with a UserAccount/context
that does *not* have access to the given `organizationId`/`storeId` and assert it's
rejected.

### Tenant isolation enforcement: application-layer, no RLS yet

**Decided: application-layer enforcement for MVP.** Every tenant-scoped query is
explicitly scoped as above, verified by automated cross-tenant integration tests — this
is mandatory, not optional, per CLAUDE.md and per decision 10. Postgres Row-Level
Security is **not** adopted yet: it would add real operational complexity (per-request
session variable management, connection-pooling interactions, RLS policy maintenance)
that isn't justified before the application-layer approach has actually been exercised
in production. It remains a future hardening option if the tenant count and threat model
later justify defense-in-depth beyond what application-layer discipline and tests give —
tracked in [open-questions.md](./open-questions.md) only as something to revisit, not as
an open MVP decision.

### Authorization checks are not cached

**Decided: `Membership`/`StoreAccess` checks are not cached initially.** Every
tenant-scoped request resolves authorization against PostgreSQL directly, which remains
the sole source of truth. Redis (or any other cache) is **not** introduced for this —
per CLAUDE.md, infrastructure like Redis is added only when a concrete requirement
justifies it, and an uncached lookup is adequate at MVP scale. This also avoids the cache
invalidation problem (e.g., on a role change) before there's any evidence lookup latency
is actually a bottleneck. This resolves the previously-open caching/performance question
(see [open-questions.md](./open-questions.md)); revisit only if lookup volume
demonstrably requires it.

### AI agents are not a special case

Every AI tool invocation carries the acting principal's context (a UserAccount, a
Customer, or an `AnonymousStorefrontSession`) and its resolved Organization/Store, and
goes through the
exact same authorization check as a human-initiated call to the same application
service — there is no AI-specific bypass path. Detailed in
[ai-architecture.md](./ai-architecture.md).
