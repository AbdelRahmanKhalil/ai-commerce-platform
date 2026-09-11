# Product & MVP Scope Proposal

Status: Proposal — pending review. Nothing here is implemented.
Revised to reflect decisions from the 2026-09-11 architecture review.

Related: [domain-model.md](./domain-model.md) · [architecture.md](./architecture.md) ·
[evolution-roadmap.md](./evolution-roadmap.md) · [open-questions.md](./open-questions.md)

## 1. Exact MVP product scope

The long-term product supports two merchant paths: **hosted storefront** and
**integrate-existing-stack**. Following the 2026-09-11 domain-model revision, these are
now understood as two possible **Channels** of the same kind of Store (a merchant
brand/commerce operation), not two Store types — see
[domain-model.md](./domain-model.md#4-tenant--organization--merchant--store-relationships).
Building both channels fully in the MVP would still dilute effort across two large
surfaces, so the MVP proves the hosted-web channel end-to-end as a real, sellable
product, while only keeping the external-integration concept documented (see
[architecture.md](./architecture.md#13-external-integration-seam)) — no integration API
or connector is built.

### Recommended MVP scope

**In scope:**

- Organization signup/onboarding (one admin UserAccount, one Organization).
- Data model supports a UserAccount belonging to multiple Organizations, and an Organization
  having multiple Stores (see [domain-model.md](./domain-model.md)), even though the
  onboarding flow only walks a merchant through creating one Store.
- Hosted web storefront on a platform subdomain (`{store}.raqana.app`), single default
  theme, no visual site builder. MVP hardcodes one implicit channel (hosted web) per
  Store — no generic Channel entity is built (see
  [domain-model.md](./domain-model.md#9-hosted-storefront-model)).
- Catalog: products with variants, flat categories/collections, single currency per
  Store.
- Inventory: single-location stock per Store, with explicit InventoryReservation records
  held through checkout (see [domain-model.md](./domain-model.md#7-initial-inventory-model)).
- Cart, checkout (guest by default — see below), and an order lifecycle tracked as
  independent OrderStatus / PaymentStatus / FulfillmentStatus (see
  [domain-model.md](./domain-model.md#10-initial-order-lifecycle)).
- **Payment: Cash on Delivery / manual payment recording only.** No online payment
  gateway in MVP, no assumption of Stripe or any specific provider. Staff record payment
  collection against an order; `PaymentStatus` is tracked independently of order
  placement and fulfillment specifically so COD is a first-class flow, not a workaround.
  Online gateway integration is a later milestone, introduced through a provider
  abstraction defined once a provider is actually selected (see
  [evolution-roadmap.md](./evolution-roadmap.md#payments--shipping)) — nothing
  gateway-shaped is built speculatively now.
- Flat-rate/manual shipping only — no carrier API integration.
- A storefront AI shopping assistant, available to **guests as well as logged-in
  customers** (customer authentication is deferred — see
  [security-and-tenancy.md](./security-and-tenancy.md#11-authentication-approach) — guest
  usage via `AnonymousStorefrontSession` is a fully valid MVP flow). It uses structured
  tools (not RAG) to search the Store's catalog and check stock, and RAG only to answer
  questions grounded in the Store's own unstructured knowledge (FAQs, return policy,
  shipping policy, sizing guide) — see
  [ai-architecture.md](./ai-architecture.md#14-ai-agent-boundaries). This is the one AI
  capability committed to being real rather than a demo, per CLAUDE.md's AI principles.
- Staff admin console: catalog, inventory, and order management; org/user/role
  management, including Store-scoped access for staff limited to specific Stores (see
  [security-and-tenancy.md](./security-and-tenancy.md#12-tenant-scoped-authorization-approach)).
- Authentication: Keycloak-issued OIDC/JWT for staff, proving identity only —
  Organization membership, Store access, and business roles are resolved from our own
  database on every request, never from the token (see
  [security-and-tenancy.md](./security-and-tenancy.md#11-authentication-approach)).
- Tenant isolation enforced in the application layer (no RLS yet), with mandatory
  automated cross-tenant tests from the first PR that touches tenant-scoped data.

**Explicitly out of scope for MVP** (long-term capabilities, deferred with a trigger
condition in [evolution-roadmap.md](./evolution-roadmap.md)):

- WhatsApp, Instagram, or any non-web channel.
- Any external-commerce connector, or even the generic Integration API/interfaces —
  the concept stays documented in [architecture.md](./architecture.md#13-external-integration-seam),
  but nothing is built until a first real integration is selected.
- Any online payment gateway (Stripe or otherwise).
- Multi-warehouse/multi-location inventory.
- Multi-currency and localization.
- Storefront theming/site-builder beyond one default theme.
- A built-out generic Channel/Connection abstraction — deferred until a second channel
  is actually being integrated.
- Cross-Store customer identity merging within an Organization (Customer identity starts
  Store-scoped — see [domain-model.md](./domain-model.md#8-customer-identity-model)).
- Customer accounts/authentication beyond guest usage.
- Staff-facing AI copilot (support-agent-assist) — the AI boundary is customer/guest
  -facing shopping assistance only in MVP (see [ai-architecture.md](./ai-architecture.md)).
- Kafka, Saga, transactional outbox, Redis, Kubernetes.
- Shipping carrier integrations, tax engines.
- Analytics beyond basic order/sales counts on the admin dashboard.

### Alternative scopes considered

| Alternative | Description | Tradeoff |
|---|---|---|
| **Integration-first MVP** | Build the external-commerce integration and conversations/AI layer first; skip the hosted storefront entirely. | Produces a more reusable core (the value prop for established merchants) but no self-serve product a small merchant can sign up and sell through immediately — weaker path to early revenue/feedback, and channel work (WhatsApp/Instagram) is itself deferred, so "omnichannel" wouldn't be demonstrable either. |
| **Storefront-only, no AI in MVP** | Ship catalog/inventory/orders/storefront without any AI capability, add AI in a fast-follow release. | Lower initial complexity and faster ship, but risks the product looking like generic e-commerce software rather than the AI-differentiated platform described in the README; also delays learning whether the tool-calling/RAG split holds up under a real feature. |
| **Both paths thin** | Build a shallow slice of both hosted storefront and external integration. | Spreads effort thin across two surfaces instead of validating one deeply; higher risk of neither path being production-quality at MVP. |

**Recommendation:** the storefront-first scope above, now unchanged in substance by the
2026-09-11 revision — only the underlying domain model (Store as brand with channels,
rather than Store as channel) and the payment/auth details changed.

## 2. Primary actors

- **Platform Admin** — Raqana staff; operates the platform itself (tenant support,
  billing oversight). Not tenant-scoped; a distinct trust boundary from all org actors.
- **Organization Owner/Admin** — the merchant's business admin; manages org settings,
  billing, staff membership. Holds an Organization-level role via `Membership`.
- **Merchant Staff** — scoped to one or more Stores within an Organization via
  `StoreAccess`, with roles `MANAGER`, `CATALOG_EDITOR`, `ORDER_MANAGER`, `SUPPORT_AGENT`
  (see [security-and-tenancy.md](./security-and-tenancy.md) for the MVP role set).
- **Customer** — an authenticated shopper identity, scoped to a Store (see
  [domain-model.md](./domain-model.md#8-customer-identity-model)). Deferred as an
  authenticated flow in MVP (see [security-and-tenancy.md](./security-and-tenancy.md)),
  though the entity exists for guest-checkout records.
- **AnonymousStorefrontSession (guest)** — an unauthenticated shopper actor, the default
  MVP shopping experience: browsing, cart, checkout, and AI shopping assistance without
  an account.
- **AI Agent** — a system actor that always acts *as* a Customer, guest
  (`AnonymousStorefrontSession`), or staff UserAccount principal (never as itself); constrained
  to a fixed toolset and bound by the same authorization as its acting principal (see
  [ai-architecture.md](./ai-architecture.md)).
- **External System** (post-MVP, concept only) — an established merchant's own commerce
  stack, connecting to a Store as a Channel. No concrete API or credential model exists
  yet — deferred until a first real integration is selected (see
  [architecture.md](./architecture.md#13-external-integration-seam)).
- **Channel Provider** (post-MVP, concept only) — WhatsApp/Instagram platforms
  delivering webhook events; verified by signature, not by identity/session. No Channel
  entity or webhook design exists yet.
