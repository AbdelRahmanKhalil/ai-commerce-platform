# 001. Modular monolith with Spring Modulith

Status: Accepted

## Context

The platform will eventually cover catalog, inventory, orders, customers,
conversations, AI, and (later) external commerce integrations across multiple channels.
Per `CLAUDE.md`, we start as a modular monolith and avoid microservices, Kafka, Sagas,
transactional outboxes, Redis, and Kubernetes until a concrete requirement justifies
them. We still need module boundaries to be real, not just a convention that erodes as
the codebase grows — especially because tenant isolation and AI-authorization
invariants (`CLAUDE.md`) depend on one module never reaching directly into another
module's repository or internals.

## Decision

- One deployable, package-by-feature, with modules mirroring the domain concepts in
  [domain-model.md](../proposals/domain-model.md): `tenancy`, `catalog`, `inventory`,
  `ordering`, `customers`, `storefront`, `conversations`, `ai`.
- `integration` is *not* part of the initial module list. It is documented as a future
  bounded context (see [architecture.md](../proposals/architecture.md#13-external-integration-seam))
  and is only created once a first real external integration is selected.
- **Spring Modulith** mechanically enforces these boundaries: module dependencies are
  verified at build/test time, and cross-module calls go through each module's public
  application service, never its repository or internal types. Spring Modulith's
  event-publication support also gives a natural place to introduce in-process domain
  events later without pulling in a message broker prematurely.
- Module boundaries are chosen so a later service extraction (see
  [evolution-roadmap.md](../proposals/evolution-roadmap.md#19-likely-future-service-extraction-boundaries))
  follows existing seams rather than inventing new ones.

## Alternatives considered

- **Microservices from day one.** Rejected: no current requirement (independent
  scaling, independent deployment cadence, team ownership boundaries) justifies the
  operational cost of a distributed system, and `CLAUDE.md` explicitly warns against
  microservices as a demonstration rather than a response to a real need.
- **Plain layered monolith with convention-only module boundaries.** Rejected: boundary
  discipline decays under time pressure without a mechanical check, and this project's
  tenant-isolation and AI-authorization invariants depend on module boundaries actually
  holding — a convention alone doesn't give that guarantee.
- **ArchUnit-only enforcement (custom rules, no Modulith).** Considered viable, but
  Spring Modulith gives boundary verification, documentation generation, and a
  ready-made path to in-process event publication in one dependency already aligned
  with the Spring ecosystem, rather than needing to author and maintain equivalent
  ArchUnit rules by hand.

## Consequences

- Module boundary violations fail fast at build/test time instead of surfacing later as
  a tenant-isolation or authorization bug.
- Adding a new module (e.g. `integration`, once justified) is a well-understood,
  low-ceremony step, not an architectural event.
- Some short-term ceremony: cross-module calls must go through a public application
  service even when a direct repository call would be more convenient during early
  development.
- Extraction into separate services later is cheaper because module boundaries already
  approximate service boundaries — but this ADR does not decide if or when extraction
  happens.

## What would cause us to revisit this decision

- A module's read/write volume, scaling needs, or team ownership diverges sharply
  enough from the rest of the system that co-deploying it is itself the constraint (see
  the extraction triggers in
  [evolution-roadmap.md](../proposals/evolution-roadmap.md#19-likely-future-service-extraction-boundaries)).
- Evidence that Spring Modulith's enforcement mechanism is insufficient or actively
  obstructive for this codebase's actual dependency shape.
