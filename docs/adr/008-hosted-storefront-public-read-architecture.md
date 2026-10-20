# 008. Hosted storefront public read architecture

Status: Accepted

## Context

Catalog (ADR 006) and Inventory (ADR 007) are complete and merged. ADR 005 already
decided that hosted web is the first MVP channel, that a backend `storefront` module
owns public Store resolution and public storefront APIs, that the backend does not
render HTML, and that Next.js renders the customer-facing storefront — those decisions
are not reopened here. ADR 005 left unspecified: how a public hostname resolves to a
Store, how a reverse-proxy/Next.js/Spring topology propagates (or avoids needing to
propagate) the original Host, what Catalog/Inventory must newly expose publicly, and
how a Next.js deployment avoids cross-tenant cache leakage when two Stores can share an
identical URL path. ADR 006 and ADR 007 each named their own revisit trigger for
exactly this situation ("a real cross-module consumer... needing product data"; "no
public Inventory Java API... because no concrete consumer exists") — this ADR records
both firing. `CLAUDE.md` requires explicit tenant scoping, fail-closed public
resolution, and forbids infrastructure/abstractions introduced without a concrete
requirement.

## Decision

- **Storefront module.** `storefront` is a new, closed Spring Modulith module. It owns
  anonymous/public storefront HTTP endpoints, hostname-to-Store resolution
  orchestration, and composition of public Catalog + Inventory data into storefront
  responses. It owns no persistence and exposes no public Java API of its own in this
  slice — no backend module consumes it.
- **Host-derived Store resolution (Spring, authoritative).** A public hostname
  resolves as exactly `<store-slug>.<configured-base-domain>`, matched by a single
  anchored pattern — never `contains`/`endsWith`/substring-suffix matching — so an
  extra label, a malicious suffix, or the wrong base domain are all rejected by the
  same mechanism rather than needing individual special-casing. The base domain is
  branding-neutral deployment configuration (`storefront.base-domain`, backed by e.g.
  `STOREFRONT_BASE_DOMAIN`), never hardcoded; a specific production value is not
  decided here. Resolution normalizes incoming Host casing and port, defines
  trailing-dot handling, and is fail-closed: every unresolvable/malformed/unknown-Store/
  wrong-base-domain case converges on the same generic 404, so a caller can never
  distinguish "malformed" from "unknown Store" from "wrong domain." Resolution reuses
  the existing `tenancy.StoreDirectory.findBySlug`, already documented as this entry
  point; it is not modified. Spring alone determines whether a Store actually exists
  and establishes the authoritative `organizationId`/`storeId` scope — nothing else in
  the system makes that determination.
- **Same-origin edge routing, no forwarded-host trust.** The customer always addresses
  `store-a.<base-domain>`. An edge router splits page traffic to Next.js from
  `/api/storefront/**` traffic to Spring, preserving the original public Host
  unmodified on the API path. Production edge routing is expected to additionally
  reject Host values unrelated to the configured storefront domain/wildcard before they
  ever reach Next.js or Spring — but this is recorded as **defense-in-depth only**;
  neither Next.js's nor Spring's own correctness may rely on the edge having done this.
  No `X-Forwarded-Host` or custom original-host header is trusted or introduced in this
  milestone, and optimizing SSR calls onto a private/internal network path is
  explicitly deferred until a concrete need exists. The reverse-proxy product/vendor
  choice is a deployment decision, not recorded here.
- **Next.js host validation before any outbound absolute URL (SSRF prevention, not
  tenant authority).** An inbound Host header is never used directly as a server-side
  fetch target until it has passed the configured storefront-host validation. Before
  Next.js uses the inbound Host to construct an SSR backend fetch URL, a canonical URL,
  or any other absolute storefront URL, it must normalize and validate that Host
  against the identical architectural shape Spring enforces —
  `<store-slug>.<configured-base-domain>`, via the same branding-neutral configuration
  — requiring: the exact configured base domain; exactly one Store label before it;
  that label obeying the accepted DNS-label shape; no arbitrary suffix matching; no
  extra labels; no userinfo/path/scheme injection; correct port handling for
  development; defined trailing-dot normalization. An invalid host fails closed (no
  outbound request is constructed). This validation is **syntax/shape validation
  only** — SSRF/routing-safety and canonical-URL safety. It performs no Store database
  lookup, makes no claim about Store existence, and is explicitly **not** tenant
  authorization. It does not create a second hostname authority: Spring independently
  re-validates and resolves the Store on every request regardless of what Next.js
  already checked, and remains the sole source of truth for whether a Store exists and
  what its authoritative Organization/Store scope is.
- **Explicit downstream tenant scoping (Spring).** Public endpoints accept no
  `organizationId`/`storeId` from the browser. Host resolution yields a
  `StoreSummary`, and its `organizationId`/`storeId` are then passed explicitly to
  every downstream Catalog/Inventory public call — no ThreadLocal/current-tenant
  context, continuing ADR 004.
- **Anonymous security boundary.** Exactly `GET`/`HEAD /api/storefront/**` are
  `permitAll`; every other HTTP method continues through the existing authenticated
  default. This prevents a future write endpoint (e.g. a Cart/Checkout `POST`) from
  accidentally becoming anonymous merely by sharing the `/api/storefront` path prefix.
  No Keycloak customer identity and no anonymous session/database record are
  introduced.
- **Catalog owns public Product/Variant visibility.** A new, second Catalog public
  Java API (`CatalogStorefrontDirectory`) is added, alongside the existing
  `CatalogVariantDirectory`. It enforces, not merely reports: a Product is publicly
  visible only when `status == ACTIVE` **and** it has at least one `ACTIVE` Variant; a
  Product that is `DRAFT`, `ARCHIVED`, or `ACTIVE` with zero `ACTIVE` Variants is hidden
  (404 by slug, absent from listings). Storefront never receives raw lifecycle state
  and never reimplements this rule itself. Inventory availability plays no part in this
  visibility rule — an all-out-of-stock but otherwise-visible Product remains visible,
  rendered sold out.
- **Category navigation is visibility-derived, not status-based.** Public category
  listing exposes only Categories containing at least one publicly visible Product (per
  the rule above); stock availability does not affect Category visibility; no
  `Category` status/lifecycle field is added.
- **Inventory's first public consumer API.** A new, first Inventory public Java API
  (`InventoryAvailabilityDirectory`) provides a single batch lookup — explicit
  `organizationId`/`storeId` plus a collection of Variant ids, returning boolean
  in-stock status per id (a missing `InventoryItem` or zero on-hand quantity is "not in
  stock"). It exposes no exact quantity to the composition layer unless a concrete
  requirement later appears, introduces no reservation semantics, and is explicitly
  documented as a browse-time signal only — not a checkout/oversell guarantee. This
  contract is not designed around Ordering's future needs.
- **Public response data.** Storefront HTTP responses may expose: Store
  name/slug/currencyCode; Category name/slug; Product title/description/slug/ordered
  image URLs and alt text; Variant id/attributes/price/inStock. They do not expose, by
  default: Organization/Store/Product/Category UUIDs, SKU, timestamps, raw lifecycle
  status values, or exact stock quantities. Variant id is the sole internal identifier
  exposed, because a future Cart needs a stable sellable-unit reference.
- **Slug-based public identity.** Product detail and Category filtering are addressed
  by slug (`?category=<slug>`, `/products/{slug}`), not UUID. An unknown `?category=`
  filter value returns 200 with an empty paginated result (a filter parameter, not a
  resource URI); a nonexistent Product slug returns 404.
- **Truthful multi-Variant pricing.** A Product's listing-card price is a range over
  its `ACTIVE` Variants' prices (a single amount when they coincide), never collapsed
  to one arbitrary value. Currency is composed into the public HTTP DTO from
  `Store.currencyCode`; Catalog storage remains currency-free at the Variant level,
  continuing ADR 006.
- **No Store publishing state.** A Store is resolvable as soon as it exists and its
  hostname is valid; a Store with no publicly visible Products returns normal
  bootstrap/listing responses with empty arrays. No `StoreStatus`/publish flag/launch
  workflow is added this milestone.
- **Reserved Store slugs.** Because `Store.slug` is now a real routable DNS label,
  Store creation must reject a small, deployment-configured set of reserved labels
  (conceptually `www`, `api`, `app`, `admin`). This is implemented in `tenancy`'s
  existing Store-creation validation, as a small separate prerequisite change (PR A.1),
  not as a hardcoded permanent list and not as a database CHECK constraint (the set is
  deployment configuration, and any production deployment must separately check
  existing Store rows for conflicts before enabling the storefront domain).
  `V1__tenancy.sql` is not modified for this.
- **No Next.js caching this milestone.** Every storefront page/fetch is dynamic
  (`force-dynamic`/`no-store` or the current equivalent) — no Full Route Cache, Data
  Cache, ISR, or CDN caching of storefront responses. The validated Host is part of
  request routing/context only, not a cache key, since there is no cache this
  milestone; any future caching must prove its cache key includes Store/host identity,
  not pathname alone, before it may be introduced.
- **No sitemap/robots this slice.** Deferred until per-Store indexing is an actual
  deployment requirement; this slice must get SSR, metadata, canonical URLs, slug URLs,
  real 404s, image alt text, and host/tenant isolation right first.
- **Implementation sequence.** ADR 008 → PR A.1 (tenancy reserved-slug validation) →
  PR A (backend: Catalog's second public API, Inventory's first public API, the
  `storefront` module, host resolution, the GET/HEAD security rule, public endpoints,
  backend tests) → PR B (Next.js storefront application, including its own host
  validation before any outbound absolute URL, and its own tests). A `frontend-engineer`
  subagent is introduced only when PR B begins.

## Alternatives considered

- **Next.js constructing its SSR/canonical URLs directly from the inbound Host without
  validating it first.** Rejected: this is an unvalidated Host-header/SSRF trust-
  boundary gap — Spring's fail-closed resolution happens too late, after Next.js has
  already issued an outbound request to an attacker-chosen origin. Next.js must
  validate the Host against the same hostname shape before using it for any outbound
  absolute URL.
- **Having Next.js perform a Store-existence database lookup to validate the Host.**
  Rejected: this would create a second hostname authority and duplicate Spring's
  tenant-resolution responsibility in a public-facing frontend process; Next.js's check
  stays syntax/shape-only, and Spring alone determines Store existence and scope.
- **Relying solely on edge-router filtering instead of this Next.js-side check.**
  Rejected: application correctness/security must not depend on a specific
  deployment's edge configuration; edge rejection is recorded as defense-in-depth,
  not as the mechanism either layer's safety is grounded in.
- **Exposing exact `onHandQuantity`/an "available" count to the public API.**
  Rejected: no concrete UI requirement for a number over a boolean, and an exact count
  invites customers to infer competitor/demand signals or attempt a future race against
  a specific number; boolean `inStock` is sufficient for browse-time display and keeps
  the contract honest about not being a reservation guarantee.
- **Showing an ACTIVE Product with zero ACTIVE Variants, with an empty-variant UI
  state.** Rejected: a product card with no price and no selectable unit is not a
  coherent storefront entity and would need its own special-cased UI; hiding keeps one
  clean invariant ("publicly visible implies at least one priced, selectable Variant")
  and needs no new frontend state.
- **Exposing all Store Categories unconditionally, independent of Product
  visibility.** Rejected: with no Category status field, this would surface dead-end
  navigation links for categories a merchant has emptied or never populated; filtering
  by Product-visibility membership needs no schema change.
- **404 for an unknown `?category=` filter value.** Rejected: a filter parameter is
  not a resource URI; an empty result for an unmatched filter is simpler and avoids
  inventing a second "not found" shape that would need to stay indistinguishable from
  the others under the fail-closed principle.
- **Trusting `X-Forwarded-Host` (or a custom original-host header) from Next.js's SSR
  calls now, to allow routing those calls over a private/internal network path.**
  Rejected for this milestone: it introduces a real trust-boundary design problem (who
  sets the header, how Spring knows it's genuine) for a latency optimization with no
  demonstrated current need; the same-origin topology sidesteps the problem entirely
  instead of solving it.
- **A database CHECK constraint enforcing reserved Store slugs.** Rejected: the
  reserved set is deployment/environment-specific configuration, not a schema-level
  invariant; a CHECK constraint would hardcode environment-dependent values into the
  database.
- **Locking a specific production base domain (e.g. a proposal-document example) into
  this ADR.** Rejected: branding is not finalized; the base domain is deployment
  configuration with no architectural reason to be fixed in an ADR.
- **A Store publish/unpublish state gating storefront reachability.** Rejected: no
  concrete merchant-onboarding/launch requirement exists yet; an empty-but-reachable
  Store is a simpler, sufficient behavior for this milestone.
- **Building sitemap/robots or Playwright/e2e coverage now.** Rejected: neither is
  required to get this slice's actual acceptance criteria (SSR, visibility, pricing,
  stock, tenant isolation) right, and a per-Store sitemap carries its own cross-tenant
  leak risk that deserves its own design pass rather than being folded in here.

## Consequences

- Catalog and Inventory each gain a second and first public Java API respectively,
  each with its own non-leakage contract to maintain alongside their own write-path
  invariants as those modules evolve.
- `storefront` has no persistence and no public API of its own; if a later module ever
  needs to call into Storefront, that is a new decision point, not something this ADR
  pre-authorizes.
- The anonymous `GET`/`HEAD`-only security carve-out means any future Storefront write
  endpoint (Cart, checkout) requires its own explicit security decision — it does not
  inherit anonymity from this one.
- Host-shape validation logic now exists independently in two places — Next.js (SSRF/
  routing safety) and Spring (authoritative resolution) — by design, not by accident;
  the two must be kept consistent with each other (same configured base domain, same
  DNS-label shape) whenever either changes, but neither may be removed in favor of
  relying on the other.
- Shipping with no Next.js caching trades some latency for guaranteed correctness;
  revisiting this requires a deliberate, Store-scoped cache-key design, not an
  incremental tweak.
- Tenancy's Store-creation path gains a small reserved-slug check as a prerequisite to
  this milestone, landing as its own PR (A.1) before Storefront's backend PR.
- A brand-new, empty Store is immediately publicly reachable (showing an empty
  catalog) the moment it's created; this is accepted as sufficient for this milestone
  and may need revisiting if merchant-onboarding timing becomes a real product
  requirement.

## What would cause us to revisit this decision

- A concrete requirement for custom merchant domains (beyond
  `<slug>.<base-domain>`) — triggers a domain-mapping design this ADR explicitly
  defers.
- A concrete latency requirement for SSR calls — triggers designing the
  private/internal routing path (and its trust boundary) this ADR deliberately
  defers.
- A concrete requirement for exact stock counts, low-stock thresholds, or any
  Inventory signal beyond boolean `inStock` — triggers revisiting
  `InventoryAvailabilityDirectory`'s contract.
- A concrete merchant-onboarding/launch-timing requirement — triggers adding a Store
  publish/unpublish state.
- A concrete SEO/indexing requirement — triggers designing per-Store sitemap/robots,
  with its own cross-tenant-leak analysis.
- A concrete requirement to cache storefront responses for performance — triggers a
  Store-scoped cache-key design, with explicit proof of cross-Store isolation before
  use.
- The reserved-subdomain set needing to grow, or needing to vary per deployment in a
  way the simple configured list can't express.
- The two independent host-validation implementations (Next.js, Spring) drifting out
  of sync in practice — triggers extracting the shape rule into one shared,
  explicitly-versioned specification/config contract rather than two hand-maintained
  copies.
