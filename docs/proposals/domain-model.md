# Domain Model Proposal

Status: Proposal — pending review. No entities/schemas are implemented from this yet.
Revised to reflect decisions from the 2026-09-11 architecture review.

Related: [product-mvp.md](./product-mvp.md) · [architecture.md](./architecture.md) ·
[security-and-tenancy.md](./security-and-tenancy.md) · [open-questions.md](./open-questions.md)

## Naming decision

Terminology is fixed as follows (Store's meaning was revised in the 2026-09-11 review —
see [Section 4](#4-tenant--organization--merchant--store-relationships)):

- **Organization** — the tenant, and the security/billing boundary. One Organization =
  one billing entity.
- **Store** — a merchant brand or commerce operation belonging to an Organization. Not a
  sales channel — see below.
- **UserAccount** — a platform login for a human staff member/owner; our local record
  mapped to the Keycloak JWT subject (see
  [security-and-tenancy.md](./security-and-tenancy.md#11-authentication-approach)).
- **Customer** — a shopper identity, scoped to a Store (see
  [Section 8](#8-customer-identity-model)).
- **Channel** — a surface a Store operates or sells through (hosted web, an external
  ecommerce site, WhatsApp, Instagram, ...). Not a built entity yet — see
  [Section 4](#4-tenant--organization--merchant--store-relationships).

"Merchant" remains a product/marketing term, not a distinct entity — a Store is what a
merchant's brand/business maps to in the data model.

**Role model (fixed as of the 2026-09-11 review):**

- **OrganizationRole** (on `Membership`) — `OWNER`, `ADMIN`, `MEMBER`. `OWNER` and
  `ADMIN` get Organization-wide access to every Store. `MEMBER` is a staff user who
  belongs to the Organization but gains no Store access on its own — business access for
  a `MEMBER` comes from `StoreAccess`.
- **Store-scoped role** (on `StoreAccess`) — `MANAGER`, `CATALOG_EDITOR`,
  `ORDER_MANAGER`, `SUPPORT_AGENT`. `StoreAccess` is included from day one (not deferred
  until a multi-Store Organization needs it) — see
  [security-and-tenancy.md](./security-and-tenancy.md#12-tenant-scoped-authorization-approach).

## 3. Core domain concepts

| Concept | Summary |
|---|---|
| Organization | Tenant root; security & billing boundary |
| UserAccount | Human login identity, global (not per-org); mapped to the Keycloak JWT subject |
| Membership | UserAccount ↔ Organization join, carries an OrganizationRole (`OWNER`/`ADMIN`/`MEMBER`) |
| StoreAccess | Grant narrowing a Membership to specific Store(s) with a Store-scoped role (`MANAGER`/`CATALOG_EDITOR`/`ORDER_MANAGER`/`SUPPORT_AGENT`); included from day one (see [security-and-tenancy.md](./security-and-tenancy.md#12-tenant-scoped-authorization-approach)) |
| Store | A merchant brand/commerce operation under an Organization; owns one catalog; will eventually connect to multiple Channels |
| Channel *(concept only, not built in MVP)* | A surface through which a Store sells or converses: hosted web, external ecommerce site, WhatsApp, Instagram |
| Product / ProductVariant | Catalog items, Store-scoped |
| Category | Flat grouping/collection, MVP only |
| InventoryItem | Stock count for a variant at a Store (`quantityOnHand`, `quantityReserved`) |
| InventoryReservation | A held quantity against an InventoryItem for a specific cart/order, with an expiry |
| Customer | Shopper identity, Store-scoped |
| ChannelIdentity | Link between a Store's Customer and a channel-native identity (phone number, IG handle, storefront login) |
| AnonymousStorefrontSession | Guest actor context for unauthenticated storefront/AI use, not tied to a Customer record unless one is later created |
| Cart / Order / OrderLine | Purchase lifecycle aggregates |
| OrderStatus / PaymentStatus / FulfillmentStatus | Independent status tracks on an Order (see [Section 10](#10-initial-order-lifecycle)) |
| Payment | A payment record against an Order (method, amount, status) |
| Conversation / Message | A messaging exchange, associated with a Store and a Channel |
| KnowledgeSource / DocumentChunk | Unstructured merchant knowledge for RAG (FAQs, policies, sizing guides), Store-scoped |
| AIToolInvocation | Metadata audit record of an AI tool call — redacted, not raw content, by default (see [ai-architecture.md](./ai-architecture.md)) |

The previously-proposed `IntegrationCredential` entity is dropped for now — per the
2026-09-11 decision, no speculative integration entities are modeled until a real
external integration is selected (see [architecture.md](./architecture.md#13-external-integration-seam)).

## 4. Tenant / organization / merchant / store relationships

### Decided: Organization is the tenant; Store is a merchant brand/commerce operation under it

A Store was originally modeled as a sales channel (`HOSTED` vs. `EXTERNAL`). That's
revised: **a Store is not itself a channel.** A Store represents one merchant
brand/commerce operation, and will eventually connect to *multiple* channels at once —
a hosted web storefront, an external ecommerce website the merchant already runs,
WhatsApp, Instagram, etc. The `HOSTED | EXTERNAL` Store type is removed.

```
Organization (tenant, security boundary)
 ├── Membership (UserAccount, OrganizationRole: OWNER | ADMIN | MEMBER) *
 │    └── StoreAccess (narrows a MEMBER to specific Store + Store-scoped role) *
 └── Store *                        -- a merchant brand / commerce operation
      ├── Product / ProductVariant * (Store-scoped catalog, one catalog per Store)
      ├── InventoryItem * → InventoryReservation *
      ├── Customer *                 (Store-scoped identity)
      │    └── ChannelIdentity *
      ├── KnowledgeSource *          (RAG inputs, Store-scoped)
      └── Channel *                  -- concept only in MVP: hosted web, external site, WhatsApp, Instagram
```

**Why this is a better fit:** "a small merchant may use the platform for their complete
storefront" and "an established merchant may keep their existing website and integrate
with our platform" are now two *channels of the same Store*, not two different Store
types. A merchant that starts hosted-only and later also connects their existing site,
or adds WhatsApp, doesn't need a new Store or a type migration — they gain a Channel.

Per the 2026-09-11 decision, the generic Channel/Connection abstraction is **not**
over-designed now: MVP does not build a `Channel` table or a pluggable channel
framework. It hardcodes the assumption that a Store has exactly one channel — its hosted
web storefront (see [Section 9](#9-hosted-storefront-model)). Building the real Channel
abstraction is deferred until a second channel is actually being integrated — see
[evolution-roadmap.md](./evolution-roadmap.md#channels-and-the-generic-channel-abstraction).

This also resolves the previously-open question of whether an "Organization → Merchant →
Store" layer is needed for agencies/multi-brand businesses: Store now fills the role that
extra layer would have played. An agency or business operating genuinely separate legal
entities is still modeled as separate Organizations, tied together by one UserAccount having
Membership in each (see [Section 5](#5-can-one-user-belong-to-multiple-organizations)),
not by a new hierarchy level.

## 5. Can one user belong to multiple organizations?

**Yes — confirmed.** `UserAccount ↔ Organization` remains many-to-many through
`Membership`, for the reason already established: retrofitting this later would mean
migrating whatever assumed a single organization per user, and the cost of supporting it
from the start is low.

**Design (revised):** per the authentication decision (see
[security-and-tenancy.md](./security-and-tenancy.md#11-authentication-approach)), the JWT
issued by Keycloak carries only the UserAccount's identity — no organization id, no
roles.
Which Organization (and Store) a client is currently operating in is **request-time
context** (e.g., a value picked in the UI and sent as a header or path segment), which
the server authorizes against `Membership` (and `StoreAccess`) on every request. This
keeps all of the multiplicity in the `Membership` table and in request-time
authorization, with nothing about "the active organization" baked into a token — a
user switching organizations doesn't require a new token, just a different requested
context, re-authorized server-side each time.

## 6. Catalog, product and product-variant modeling

**Decided: Product and ProductVariant remain Store-scoped.** A Store owns one catalog,
which can eventually be exposed across multiple Channels (hosted web, external site,
WhatsApp) without duplication — the catalog doesn't change shape per channel, only how
it's presented does.

- **Product** — Store-scoped: title, description, status, category, base attributes.
- **ProductVariant** — SKU, attribute values (size/color/etc.), price, weight/dimensions
  (captured now because shipping will need them later, at no extra modeling cost today).
- **Category** — flat collections/tags for MVP. Hierarchical taxonomy deferred.
- **Price / currency (decided):** one currency per Store in MVP. Store carries an
  ISO-4217 `currencyCode` (`EGP` initially, for our target market). All monetary amounts
  use a decimal/`BigDecimal` type, never floating-point, per CLAUDE.md. Multi-currency
  pricing is deferred — the schema is not generalized for it now, and a future
  multi-currency need is accepted as a migration rather than built speculatively today.
- **Media** — image URLs referenced from external object storage; no asset-management
  domain modeling yet.

Note this only matters at real scale once an Organization operates multiple Stores; MVP
onboarding still guides a merchant to create one Store.

## 7. Initial inventory model

- **InventoryItem** per (ProductVariant, Store) — `quantityOnHand`, `quantityReserved`.
  No warehouses, no backorders, no external inventory sync in MVP.
- **InventoryReservation** (new) — tracks an individual hold against an InventoryItem:
  `(id, inventory_item_id, cart_id | order_id, quantity, status, expires_at)`. This
  replaces an implicit "reservation" with an explicit, queryable record, so:
  - Multiple concurrent reservations against the same InventoryItem are individually
    visible and expirable, rather than a single aggregate counter with no history.
  - A background sweep can release expired reservations (`status = Expired`,
    `quantityReserved` decremented) without guessing which cart it belonged to.
  - On order confirmation, a reservation is consumed: `quantityOnHand` decremented,
    `quantityReserved` decremented, `status = Consumed`.
  - On cart abandonment/cancellation, a reservation is released:
    `quantityReserved` decremented, `status = Released`, no change to `quantityOnHand`.
- **Concurrency:** overselling under concurrent checkouts is a correctness risk with real
  financial impact. Creating/consuming/releasing a reservation must happen inside a
  transaction that locks the InventoryItem row (`SELECT ... FOR UPDATE`) or uses
  optimistic concurrency (a version column). Per CLAUDE.md, this needs automated
  concurrency tests, not just unit tests — this requirement is unchanged from the
  original proposal and is restated here because InventoryReservation is where those
  tests will actually be written.
- Multi-warehouse inventory and merchant-side inventory sync (once external channel
  connections exist) remain explicit future work — see
  [evolution-roadmap.md](./evolution-roadmap.md).

## 8. Customer identity model

**Revised: Customer identity is Store-scoped, not Organization-scoped.**

The earlier proposal scoped Customer to the Organization on the reasoning that
omnichannel unification requires recognizing the same shopper everywhere. That's still
the long-term goal, but it's revised to start narrower: a Customer belongs to a Store
(the merchant brand they're actually shopping with), and **cross-Store identity merging
within the same Organization is explicit future work**, not assumed from day one. This
avoids prematurely deciding how identity should merge across genuinely different brands
under one Organization before there's a concrete case requiring it (see
[open-questions.md](./open-questions.md)). **Confirmed:** cross-Store Customer identity
remains completely independent in MVP — no speculative global-customer or merge entity
is modeled ahead of a concrete need.

- **Guest checkout** creates a Store-scoped Customer record without requiring an
  account, or — for browsing/AI interactions that never reach checkout — no Customer
  record at all (see `AnonymousStorefrontSession` below).
- **Optional account** for return customers, per Store. Per the authentication decision
  (see [security-and-tenancy.md](./security-and-tenancy.md#11-authentication-approach)),
  customer authentication itself is deferred — guest storefront usage (browsing,
  cart, checkout, and AI shopping assistance) is a fully valid MVP flow without any
  customer login.
- **ChannelIdentity** — `(type, external_id, customer_id)` linking a channel-native
  identity (WhatsApp phone number, Instagram handle, storefront login) to a Store's
  Customer. This unifies the same shopper across *that Store's* channels once channels
  beyond hosted-web exist. Still modeled as a data shape now (identity-linking is
  difficult to retrofit once conversation history exists under disconnected identities),
  even though no non-web channel is built in MVP.
- **AnonymousStorefrontSession** — a lightweight, unauthenticated actor context for a
  guest's storefront visit (cart, browsing, AI chat), identified by a session
  token/cookie rather than any account. It may later be associated with a Customer
  record (at checkout, or if the guest registers), but has no required link to one. This
  is what lets the AI shopping assistant serve guests (see
  [ai-architecture.md](./ai-architecture.md#14-ai-agent-boundaries)) without requiring
  customer authentication.

## 9. Hosted storefront model

- The backend `storefront` module is served from the same modular monolith as everything
  else in MVP — no separate backend storefront *service* (see
  [architecture.md](./architecture.md)). It does not render HTML: it owns public Store
  resolution and the public storefront query/command APIs. A separate Next.js
  application renders the customer-facing storefront by calling those APIs. This is a
  frontend/backend split, not a backend service extraction.
- **Tenant/Store resolution for public, unauthenticated storefront requests:** resolve
  the Store by subdomain (`{store}.raqana.app`) or mapped custom domain (future) via a
  server-side lookup. Per CLAUDE.md, the resolved Store is never taken from a
  client-suppliable header/param — the hostname resolution *is* the trust boundary and
  must fail closed (unknown host → 404, never a default tenant).
- MVP does not model a `Channel` entity (see [Section 4](#4-tenant--organization--merchant--store-relationships));
  it hardcodes the assumption that every Store has exactly one channel — its hosted web
  storefront — reachable at that Store's subdomain. There is nothing to configure or
  toggle yet; a Store simply has a storefront.
- One default theme/template for MVP; no drag-and-drop site builder (a large product
  surface in its own right, explicitly deferred).
- Custom domain mapping and TLS provisioning are deferred; the domain→Store resolution
  mechanism is designed so adding custom domains later doesn't change the resolution
  approach, only its lookup source.

## 10. Initial order lifecycle

**Revised: order state is tracked as three independent statuses rather than one combined
lifecycle**, so that payment method (in particular Cash on Delivery) doesn't distort the
order or fulfillment flow. Exact enum values are proposal-level and may change during
implementation.

- **OrderStatus** — `Placed → Completed | Cancelled`. Tracks the order itself, not
  payment or fulfillment detail.
- **PaymentStatus** — `Pending → Paid | Failed`, with `Refunded` / `PartiallyRefunded` as
  later states. Independent of OrderStatus and FulfillmentStatus.
- **FulfillmentStatus** — `Unfulfilled → Processing → Shipped → Delivered`, with
  `Returned` as a later state. Independent of the other two.

**Why this supports COD cleanly:** in the original combined lifecycle
(`Cart → Checkout → PaymentPending → Paid → Processing → Fulfilled → Completed`),
fulfillment implicitly couldn't start until payment succeeded — a fine assumption for a
card-up-front flow, wrong for Cash on Delivery, where payment is collected *at* delivery.
With independent statuses: an order is `Placed` with `PaymentStatus = Pending`
immediately (no payment has happened yet), `FulfillmentStatus` progresses normally
through `Processing → Shipped → Delivered`, and `PaymentStatus` only flips to `Paid` when
cash collection is recorded (by the courier or by staff) — which in the COD case
typically coincides with or follows delivery, not checkout.

- **Business rule (not a single state machine):** `OrderStatus = Completed` requires
  `FulfillmentStatus = Delivered` **and** `PaymentStatus = Paid`.
- **Cancellation:** allowed while `FulfillmentStatus` is `Unfulfilled` or `Processing`,
  releasing any InventoryReservation. Once `Shipped`, undoing the order goes through a
  return flow (`FulfillmentStatus = Returned`) rather than plain cancellation.
- **Payment model, MVP:** a `Payment` record per Order — `method = COD | Manual`,
  `amount`, `status` (mirrors PaymentStatus), `recordedBy`, `recordedAt`. There is no
  external payment gateway call anywhere in this flow (see
  [architecture.md](./architecture.md#16-which-operations-should-remain-synchronous)) —
  the entire order-placement transaction (order + InventoryReservation consumption +
  Payment record) is internal to one Postgres transaction. Online payment gateway
  integration is a later milestone, introduced via a provider abstraction defined at
  that time (see [evolution-roadmap.md](./evolution-roadmap.md#payments--shipping)) —
  none of that abstraction is scaffolded speculatively now.
- **Decided: COD/manual payment MVP scope is deliberately narrow.** Partial payment
  against an order and switching payment methods mid-flow are **not** supported in MVP —
  an order has exactly one `Payment` record at its declared method. More advanced
  COD/payment edge cases (partial collection, method changes, disputed collection) are
  explicit future work, not designed here.

### Future ordering design notes (not implemented now)

These are recorded so they aren't forgotten when ordering is actually built, not as
scope for the current revision:

- **OrderLine must eventually snapshot the purchased variant's commercial data** —
  SKU, name, price at time of order — rather than depending solely on live, mutable
  `Product`/`ProductVariant` data. Without this, editing or deleting a product later
  would silently rewrite historical order records.
- **Order-time delivery/contact details should likewise be historical snapshots** on
  the Order, not a live reference to a Customer's current address/contact info, for the
  same reason.
