# Project Context

This repository contains a production-quality multi-tenant SaaS platform for omnichannel commerce.

The product is an AI-powered omnichannel commerce platform for merchants
and brands.

It can either:
1. provide a merchant with a hosted online storefront, or
2. integrate with an existing commerce stack.

It will eventually unify products, inventory, orders, customers,
conversations, support, analytics, and AI-assisted commerce across web,
WhatsApp, Instagram, and future channels.

# Engineering Principles

## Architecture

- Start as a modular monolith.
- Prefer a simple architecture until real requirements justify complexity.
- Maintain explicit module boundaries.
- Do not introduce microservices merely to demonstrate microservices.
- Do not introduce Kafka, Saga, transactional outbox, Redis, Kubernetes,
  or other infrastructure until a concrete requirement justifies them.
- When distributed components are introduced, document why.
- Prefer package-by-feature/module over global technical layers.
- Modules must not reach directly into another module's repositories or
  internal implementation.

## Multi-tenancy

- This is a multi-tenant SaaS.
- Tenant isolation is a security invariant.
- Never trust a tenant/store identifier merely because the client supplied it.
- Every tenant-scoped operation must prove that the authenticated principal
  has access to that tenant.
- Public storefront tenant/store resolution must also be explicit and safe.
- Cross-tenant data access must have automated tests.

## Security

- Authentication and authorization must use established standards.
- Prefer OAuth 2.0 / OpenID Connect and JWT rather than inventing
  authentication protocols.
- Authorization belongs in the application, not only in the UI.
- AI agents must obey exactly the same authorization and business rules
  as human callers.
- The LLM is an untrusted component.
- An LLM must never directly modify the database.
- AI actions must go through controlled application APIs/tools.

## Data

- PostgreSQL will be the primary transactional source of truth unless an
  architectural decision explicitly changes this.
- Database migrations must be version-controlled.
- Money must never use floating-point types.
- Time handling should use explicit UTC-aware types where appropriate.
- Avoid premature database abstractions.

## Distributed Systems

- Synchronous APIs are the default when synchronous behavior is appropriate.
- Kafka is for genuine asynchronous/event-driven requirements, not normal CRUD.
- If a database transaction must reliably result in an external event,
  consider the transactional outbox pattern.
- Saga is only appropriate when one business workflow spans independently
  transactional boundaries and requires compensation.
- External callbacks and retryable commands should be designed for idempotency.

## AI

- AI must solve actual product problems rather than exist as a demo.
- RAG responses must be grounded in tenant-authorized knowledge.
- Retrieval must enforce tenant isolation.
- Tool calls must be validated and authorized server-side.
- Never rely on an LLM response as proof that an action is permitted.
- AI provider-specific code should not unnecessarily infect the domain model.

## Quality

- Do not implement features outside the current task without asking.
- Do not add dependencies without a reason.
- Prefer explicit code over unnecessary abstraction.
- Avoid generic frameworks/base classes created only for future possibilities.
- Security-critical, concurrency-sensitive, payment, tenant-isolation,
  and distributed-workflow behavior requires automated tests.
- Use integration tests where database behavior or boundaries matter.
- Run relevant tests after changes.
- Keep documentation synchronized with important architectural decisions.

## Agent Workflow

For a non-trivial task:

1. Read the relevant project documentation first.
2. Explain the proposed change before making large architectural changes.
3. Implement only the agreed scope.
4. Run relevant tests.
5. Report what changed and any unresolved tradeoffs.
6. Do not silently make major product or architecture decisions.