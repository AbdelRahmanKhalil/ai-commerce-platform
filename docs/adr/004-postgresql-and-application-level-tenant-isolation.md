# 004. PostgreSQL with application-level tenant isolation

Status: Accepted

## Context

`CLAUDE.md` establishes PostgreSQL as the primary transactional source of truth and
states tenant isolation is a security invariant: never trust a client-supplied
tenant/store identifier, and every tenant-scoped operation must prove the authenticated
principal has access to that tenant. Since the JWT carries no Organization/Store claims
to cross-check against (see [ADR 003](./003-keycloak-identity-and-application-authorization.md)),
the `Membership`/`StoreAccess` lookup is the only source of truth for access, and it must
be applied consistently across every tenant-scoped query in the codebase.

## Decision

- **PostgreSQL, single shared schema**, remains the sole source of truth for tenancy and
  business data. No per-tenant database or schema is introduced.
- **Application-layer tenant isolation for MVP.** Every tenant-scoped repository and
  application-service method signature takes the scoping id(s) (`organizationId`, and
  `storeId` where applicable) as **explicit parameters** — never resolved implicitly
  from a thread-local "current tenant" inside the repository. A query missing its
  scoping parameter is visible in the method signature and in code review.
- **Postgres Row-Level Security (RLS) is not adopted yet.** It would add real
  operational complexity (per-request session variable management, connection-pooling
  interactions, RLS policy maintenance) not justified before the application-layer
  approach has been exercised in production.
- **Automated cross-tenant integration tests are mandatory, not optional**, for any PR
  touching tenant-scoped data: call an application service with a principal/context that
  does *not* have access to the given `organizationId`/`storeId` and assert it's
  rejected. This is the concrete verification that the explicit-scoping convention is
  actually followed.
- AI tool invocations go through the same explicitly-scoped application services as
  human-initiated calls — there is no AI-specific data-access path that could bypass
  this.

## Alternatives considered

- **Postgres Row-Level Security with session variables.** Rejected for MVP: adds
  meaningful operational complexity (setting/clearing session variables correctly under
  connection pooling, maintaining RLS policies alongside application code) without a
  demonstrated need beyond what disciplined application-layer scoping plus mandatory
  tests already provides. Remains a future defense-in-depth option.
- **Schema-per-tenant or database-per-tenant.** Rejected: at MVP scale this adds
  migration and connection-management overhead disproportionate to the actual tenant
  isolation problem, which application-layer scoping already solves; also complicates
  cross-tenant platform-admin queries (e.g. billing oversight).
- **Implicit thread-local tenant context ("current tenant" auto-applied by a base
  repository).** Rejected: makes a missing or incorrect scope invisible in code review —
  exactly the failure mode CLAUDE.md's tenant-isolation invariant is meant to prevent.
  Explicit parameters make the scoping decision reviewable at each call site.

## Consequences

- Every new tenant-scoped repository/service method must be written with explicit
  scoping parameters from day one — no "add scoping later" path exists without touching
  every call site.
- Tenant isolation correctness depends on code-review discipline and test coverage
  rather than a database-enforced guarantee — the mandatory cross-tenant test
  requirement exists specifically to make this verifiable rather than aspirational.
- No RLS-specific operational tooling (session variable plumbing, policy management) is
  needed for MVP.
- If a bug ever ships that omits scoping on a tenant-scoped query, there is no
  database-level backstop catching it — this is the accepted tradeoff for MVP
  simplicity, made explicit here rather than left implicit.

## What would cause us to revisit this decision

- A cross-tenant data-isolation incident (in testing or production) that application-layer
  discipline and tests failed to catch — direct evidence the current approach is
  insufficient.
- Tenant count or threat model growing to a point (e.g. handling regulated data, or
  enterprise customers requiring defense-in-depth guarantees) that justifies RLS as an
  additional enforcement layer on top of, not instead of, application-layer scoping.
- Evidence that connection-pooling or schema-per-tenant tradeoffs would meaningfully
  improve isolation or performance at a scale the current approach no longer serves
  well.
