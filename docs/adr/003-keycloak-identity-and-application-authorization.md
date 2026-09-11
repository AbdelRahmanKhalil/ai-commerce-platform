# 003. Keycloak for identity, application-owned authorization

Status: Accepted

## Context

`CLAUDE.md` requires standards-based authentication (OAuth 2.0/OIDC, JWT) rather than an
invented protocol, and requires that authorization live in the application, not only in
the UI or the identity provider. This platform's authorization model is dynamic and
many-to-many — one `UserAccount` can belong to multiple Organizations, and access can be
narrowed per Store via `StoreAccess` (see
[ADR 002](./002-tenancy-and-store-model.md)) — which does not map cleanly onto a
typical IdP's realm/group/role model without either duplicating that model inside the
IdP or accepting the IdP as a second source of truth for tenancy.

## Decision

- **Keycloak** is the OIDC/OAuth2 identity provider for staff `UserAccount`s, using
  **one shared platform realm** for all Organizations — not one realm per Organization.
- **Keycloak proves identity only.** It authenticates a `UserAccount` and issues a JWT
  whose subject identifies that `UserAccount`. The JWT carries no Organization id, Store
  id, or role/business-permission claims.
- **`UserAccount` maps 1:1 to the Keycloak JWT subject.** Organization membership,
  Store access, and business roles live entirely in our PostgreSQL database
  (`Membership`, `StoreAccess`), owned by the `tenancy` module.
- **Every tenant-scoped request resolves authorization from `Membership`/`StoreAccess`
  server-side**, using the Organization/Store the client requests as untrusted resource
  context, never as a trusted claim. There is no shortcut through the token.
- **Authorization checks are not cached.** PostgreSQL is queried directly on every
  request; Redis (or any other cache) is not introduced for this. This avoids the cache
  invalidation problem (e.g. on a role change) before there is any evidence lookup
  latency is actually a bottleneck.
- AI tool invocations are authorized identically to human-initiated calls to the same
  application service — the LLM choosing to call a tool is never treated as proof the
  action is permitted (per `CLAUDE.md`).

## Alternatives considered

- **One Keycloak realm per Organization.** Rejected for MVP: adds real operational
  overhead (realm provisioning on signup, staff-invite flow complexity) for an isolation
  property (containing a compromised realm to one Organization) that application-layer
  tenant isolation already needs to provide regardless, since the JWT is never trusted
  for authorization anyway.
- **Encoding Organization/role claims in the JWT.** Rejected: would require reissuing
  tokens on every role or Organization-membership change, and would create a second
  source of truth for authorization data that must stay consistent with our database —
  exactly what `CLAUDE.md`'s "authorization belongs in the application" principle warns
  against.
- **Custom-built authentication.** Rejected outright per `CLAUDE.md` — standards-based
  auth (OIDC/JWT) is required rather than inventing a protocol.
- **Caching `Membership`/`StoreAccess` lookups (e.g. in Redis).** Rejected for MVP: no
  concrete performance requirement justifies the added infrastructure and cache
  invalidation complexity yet, per `CLAUDE.md`'s "don't introduce Redis until a concrete
  requirement justifies it."

## Consequences

- Every tenant-scoped request pays a database lookup for authorization; acceptable at
  MVP scale, but a real latency or load concern to watch as tenant count grows.
- A `UserAccount` switching which Organization/Store it's operating in requires no new
  token — only a different request-time context, re-authorized server-side each time.
- A role or `StoreAccess` change takes effect immediately (no token to invalidate),
  which is a security benefit relative to a claims-in-JWT design.
- A compromised Keycloak realm affects all Organizations' staff logins, not just one —
  this risk is accepted because tenant isolation is enforced at the application layer
  regardless of what the token contains, and Keycloak has no tenancy data to expose.
- Customer authentication is explicitly out of scope for this decision (deferred per the
  product/MVP proposal); whether customer accounts later go through Keycloak or a
  separate mechanism remains an open question.

## What would cause us to revisit this decision

- A compliance or enterprise-customer requirement for per-tenant IdP isolation
  (dedicated realm, or even a separate IdP instance) that outweighs the added
  operational cost.
- Evidence that per-request `Membership`/`StoreAccess` lookups are a measurable
  performance bottleneck, at which point a caching strategy (with an explicit
  invalidation design) would need to be designed, not retrofitted ad hoc.
- Introduction of customer authentication, which will need its own explicit decision on
  whether it shares the Keycloak realm or uses a separate mechanism.
