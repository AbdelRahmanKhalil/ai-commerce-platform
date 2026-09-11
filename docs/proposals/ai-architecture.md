# AI Architecture Proposal

Status: Proposal — pending review. No AI/RAG code is implemented from this yet.
Revised to reflect decisions from the 2026-09-11 architecture review.

Related: [security-and-tenancy.md](./security-and-tenancy.md) · [domain-model.md](./domain-model.md) ·
[product-mvp.md](./product-mvp.md) · [open-questions.md](./open-questions.md)

## 14. AI-agent boundaries

Per CLAUDE.md: the LLM is an untrusted component, it must never touch the database
directly, and AI agents must obey exactly the same authorization and business rules as
human callers. Concretely:

- The AI never calls a repository or the database directly. It calls a fixed set of
  **Tools**, each a thin wrapper around an existing application service — the same
  service a human-triggered API endpoint calls. If a tool didn't exist as an authorized
  application-service call before the AI feature existed, it doesn't become one just
  because the AI needs it.
- Every tool invocation is executed **as the acting principal**, carrying that
  principal's resolved Organization/Store context, and is checked by the same
  authorization logic described in
  [security-and-tenancy.md](./security-and-tenancy.md#12-tenant-scoped-authorization-approach).
  The LLM choosing to call a tool is never treated as proof that the action is permitted
  — the tool implementation re-checks authorization itself.
- **Decided: the acting principal for AI purposes includes guests.** An
  `AnonymousStorefrontSession` (see [domain-model.md](./domain-model.md#8-customer-identity-model))
  is a valid context for the storefront shopping assistant, alongside an authenticated
  Customer or staff UserAccount. This is what lets guest storefront visitors use the AI
  assistant even though customer authentication is deferred (see
  [security-and-tenancy.md](./security-and-tenancy.md#11-authentication-approach)) —
  the session still has a bounded, checkable identity (a session token tied to one Store
  and one cart), it's just not backed by an account.
- **Decided: structured data goes through tools/query APIs, not RAG.** Catalog and
  inventory are structured, transactionally-consistent data — retrieving them through a
  vector search would be both slower and looser (approximate similarity instead of an
  exact filter/lookup) than just querying them. **MVP AI toolset:**
  - `SearchCatalog` — a structured query tool (filters/keyword search) against the
    Store's `catalog` module, not RAG.
  - `CheckInventory` — a structured query against `inventory`.
  - `AnswerFromKnowledgeBase` — the one RAG-backed tool, scoped to unstructured merchant
    knowledge (see [Section 15](#15-rag-boundaries-and-tenant-isolation)).
  - `AddToCart` — the one state-changing action tool in MVP.

  It is explicitly **not** permitted to: apply discounts, modify or cancel orders, touch
  payments, access another customer's data, or change the catalog. Staff-facing AI
  (support-agent-assist, autonomous WhatsApp sales agent) is out of scope for MVP (see
  [product-mvp.md](./product-mvp.md)) — when it arrives, it extends the toolset, not the
  trust model.

### AI auditing: metadata and redaction, not raw content by default

**Revised per decision 11:** blindly persisting raw AI tool inputs/outputs risks storing
sensitive content (whatever a shopper types, which may include personal information)
indefinitely in a log table nobody designed retention or access controls for. Instead:

- The audit record for each tool invocation captures **metadata**: timestamp, tool name,
  acting principal type and id (UserAccount / Customer / AnonymousStorefrontSession), resolved
  Organization/Store, success/failure, and an error class on failure — enough to answer
  "did this AI agent do something it shouldn't have" without storing the content of what
  was said.
- Raw prompt/response content is **not** persisted by default. If raw content retention
  is later needed for quality/debugging, it should be an explicit, separately
  access-controlled, short-retention store — not the same audit log used for security
  review — and needs its own redaction policy (e.g., stripping free-text PII) before it's
  built. The exact redaction rules are an open design question, not settled at proposal
  level — see [open-questions.md](./open-questions.md).
- This still satisfies "AI actions get the same audit scrutiny as human actions": the
  metadata record is what makes "the AI obeyed the same rules" independently verifiable,
  without requiring raw-content capture to do it.

## 15. RAG boundaries and tenant isolation

Per CLAUDE.md: RAG responses must be grounded in tenant-authorized knowledge, and
retrieval must enforce tenant isolation — this is treated as a security property, not
just a relevance/quality one.

- **Decided: RAG is scoped to unstructured merchant knowledge only** — FAQs, return
  policies, shipping policies, sizing guides, and similar documents a merchant provides.
  It is explicitly **not** used to retrieve catalog or inventory data (see
  [Section 14](#14-ai-agent-boundaries)) — those go through structured tools.
- **Scope of a KnowledgeSource:** Store-scoped (see
  [domain-model.md](./domain-model.md#3-core-domain-concepts)), since the catalog,
  customers, and brand identity it supports are all Store-scoped. The Organization
  remains the outer security boundary; Store is the finer partition within it, and
  retrieval filtering must respect both.
- **Tenant/Store partitioning of the vector store:** every retrieval query is filtered by
  Organization id **and** Store id server-side, derived from the same resolved context
  used everywhere else — never from a value the request could supply. A retrieval query
  with no Store filter should be structurally impossible, not merely convention, mirroring
  the explicit-scoping convention in
  [security-and-tenancy.md](./security-and-tenancy.md#explicit-scoping-convention).
- **Decided: pgvector inside the existing Postgres** remains the initial vector store —
  no dedicated vector database. One less infrastructure dependency; tenant/Store
  filtering is an ordinary `WHERE` clause on columns already subject to the same
  tenancy discipline as every other table; adequate ANN performance at MVP scale and MVP
  knowledge-base size (FAQs/policies, not a large catalog). Revisit only if knowledge-base
  size or query volume demonstrably outgrows it.
- **Embedding pipeline:** re-embed synchronously (or via a simple in-process job) on
  KnowledgeSource create/update. No message broker needed at MVP scale — see
  [architecture.md](./architecture.md#16-which-operations-should-remain-synchronous).
- **Grounding rule:** `AnswerFromKnowledgeBase` answers only from retrieved,
  Store-authorized documents. If retrieval returns nothing relevant, it must say so
  rather than fall back on the model's general knowledge.
- **Provider isolation:** AI-provider-specific code (prompt formats, SDK types) stays
  inside the `ai` module and is not allowed to leak into `catalog`/`ordering`/etc. domain
  types, per CLAUDE.md's "AI provider-specific code should not unnecessarily infect the
  domain model."
