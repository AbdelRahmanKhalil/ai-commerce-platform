# 006. Catalog domain model

Status: Accepted

## Context

The `tenancy` module (see [ADR 002](./002-tenancy-and-store-model.md) and
[ADR 004](./004-postgresql-and-application-level-tenant-isolation.md)) is complete and
merged; `catalog` is the next module in the modular monolith described in
[ADR 001](./001-modular-monolith-and-spring-modulith.md). `CLAUDE.md` requires every
tenant-scoped operation to prove access explicitly rather than trusting a client-
supplied identifier, and separately warns against generic frameworks, base classes, or
abstractions built for imagined future needs rather than a concrete current
requirement. The domain-model proposal ([domain-model.md](../proposals/domain-model.md))
sketched `Product`/`ProductVariant`/`Category` at a high level — including
weight/dimensions on `ProductVariant` and Category as "flat collections/tags" — but did
not settle the concrete schema, invariants, lifecycle, or authorization wiring a first
implementation needs. We need to decide: how `Product`, `ProductVariant`, and
`Category` relate to each other and to `Store`; how price, SKU, and variation
("options") are modeled; whether/how these rows are ever deleted; and how catalog's
write API is authorized against the Store-scoped and Organization-wide roles ADR 002
already defines.

## Decision

- **`Product`, `ProductVariant`, and `Category` are Store-scoped.** Every row carries
  both `organization_id` and `store_id`, continuing the explicit-scoping requirement of
  ADR 004. Composite Store/Organization foreign keys (mirroring the pattern in
  `V1__tenancy.sql`) are used as defense-in-depth on catalog tables — this is a
  continuation of an existing pattern, not something ADR 004 itself mandates.
- **Every Product has at least one ProductVariant.** This is an **application
  invariant**, enforced by the catalog write API and its transaction boundary: creating
  a Product atomically creates its initial Variant(s) in the same transaction.
  PostgreSQL cannot express "at least one child row must exist" as a declarative
  constraint, so there is deliberately no database-level guarantee of this rule — it
  holds because the only write path that creates a Product also creates a Variant, and
  because Variant rows are never hard-deleted (only deactivated, see below), so no
  ordinary application operation can later reduce a Product to zero Variants.
- **Simple products are one Variant with `attributes = {}`.** There is no separate
  "simple product" type and no nullable-Variant special case — a Product without
  meaningful variation is simply a Product with exactly one Variant whose attribute map
  is empty.
- **`ProductVariant.sku` is mandatory and unique per Store, case-insensitively,
  preserving the merchant's original casing.** The same SKU value may be reused across
  different Stores. This is enforced with a Postgres expression unique index over the
  Store id and the lower-cased SKU, not a global unique constraint and not a `citext`
  column.
- **Price belongs to `ProductVariant`,** stored as a fixed-point decimal amount, never
  floating-point (per `CLAUDE.md`). **Currency is never duplicated on Variant** — it is
  always resolved from the owning Store's `currencyCode` (ADR 002), which remains one
  currency per Store for MVP.
- **`Product` and `Category` are related many-to-many, and flat.** There is no Category
  hierarchy and no separate Collection aggregate in this slice.
- **`ProductVariant.attributes` is a flat `String -> String` map, persisted as JSONB,**
  for MVP — not a normalized option/option-value schema. Two Variants of the same
  Product may not share an identical attribute combination; this is enforced at the
  database level via a uniqueness constraint over the Product and its attribute map,
  relying on JSONB's canonical-form equality (key order does not defeat the
  constraint).
- **`Product` and `ProductVariant` use lifecycle status fields and are never hard-
  deleted.** (`Product`: `DRAFT`/`ACTIVE`/`ARCHIVED`; `ProductVariant`:
  `ACTIVE`/`INACTIVE`.) **`Category` and `ProductImage` are ordinary mutable rows and
  may be hard-deleted** — deleting a Category cascades only its Product-Category
  association rows, never the Products themselves.
- **Product images are ordered child rows of a Product.** Ordering is supplied by the
  client as array order at the API boundary, not as an explicit client-supplied
  `position` field — but the database still stores an explicit `position` column and
  still enforces its ordering constraints (non-negative, unique per Product).
- **Shipping/package dimensions are not modeled in this slice.** This revises the
  weight/dimensions sketch in the original `domain-model.md` proposal; dimensions are
  deferred until a concrete shipping/fulfillment requirement exists.
- **Catalog write access follows the Store-scoped and Organization-wide roles already
  defined in ADR 002:** `OWNER`/`ADMIN` (Organization-wide) and `MANAGER`/
  `CATALOG_EDITOR` (Store-scoped) may write catalog data; `ORDER_MANAGER` and
  `SUPPORT_AGENT` are read-only against catalog. All checks go through tenancy's public
  `TenancyAuthorization` API — catalog never imports tenancy's entities, repositories,
  or `tenancy.internal` types.
- **`catalog` is introduced as a closed Spring Modulith module,** following the same
  shape as `tenancy`: a small public API in the module's root package, entities/
  repositories/internal services under `catalog.internal`, controllers under
  `catalog.web`.
- **No public `catalog` Java API is introduced in this slice.** Catalog exposes only its
  HTTP surface; a root-package service interface for other modules to call is deferred
  because no concrete cross-module consumer exists yet.
- **`V2__catalog.sql` introduces the catalog schema as a new migration; `V1__tenancy.sql`
  is not modified.**
- **Inventory, the public storefront APIs, ordering, customers, and AI/RAG remain out of
  scope** for this slice.

## Alternatives considered

- **Optional Variants — a Product may exist with zero Variants, with price/SKU living
  directly on Product for "simple" products.** Rejected: this would require two
  different pricing/inventory-linkage code paths (Product-level vs. Variant-level)
  throughout catalog and, later, inventory and ordering. Requiring every Product to
  have exactly one Variant when it has no real variation (`attributes = {}`) keeps
  exactly one path through the whole system.
- **A single Category per Product (`Product.categoryId`), instead of many-to-many.**
  Rejected: merchants routinely want a Product to appear under more than one Category
  (e.g. "New Arrivals" and "Shoes"); retrofitting many-to-many onto a single foreign key
  later would require a data migration, while the many-to-many join table costs little
  to include now.
- **A Category hierarchy or a separate Collection aggregate now.** Rejected: no
  concrete requirement for nested categories or curated collections exists yet. A flat
  many-to-many relationship is the simplest structure that satisfies the current need,
  and a hierarchy (e.g. a nullable parent reference) can be added later without
  breaking the flat model already in place.
- **Normalized option/option-value tables for Variant attributes**, instead of a flat
  JSONB map. Rejected for now: nothing in the current requirements needs faceted
  search, per-option display metadata (ordering, swatches, shared option definitions
  across Products), or cross-product option reuse — a flat JSONB map is sufficient to
  express "what distinguishes this Variant from its siblings" at MVP. This is
  explicitly revisited (see below) once faceted storefront search or richer option
  metadata becomes a real requirement, since that would require migrating attributes
  out of JSONB into normalized tables.
- **Universal soft deletion — a single `deleted_at`/`is_deleted` convention applied
  uniformly across every catalog entity.** Rejected: `Product` and `ProductVariant`
  already need a richer lifecycle (`DRAFT`/`ACTIVE`/`ARCHIVED`,
  `ACTIVE`/`INACTIVE`) that a boolean soft-delete flag doesn't express, while `Category`
  and `ProductImage` have no such lifecycle need and are safe to hard-delete. Forcing a
  single soft-delete convention onto all of them regardless of whether each entity
  needs it would be exactly the kind of generic abstraction `CLAUDE.md` warns against —
  each entity's deletion behavior is decided on its own actual requirement instead.

## Consequences

- Every catalog write path that creates a Product must also create at least one
  Variant in the same transaction; any future write path that could persist a Product
  without a Variant would silently violate this invariant, since nothing at the
  database level would catch it. This must remain a reviewed property of the write API,
  not something assumed to be self-enforcing.
- Anywhere a Variant's price is displayed, compared, or totaled, the owning Store's
  currency must be looked up via tenancy's public API — currency is never available
  directly on the Variant.
- SKU uniqueness relies on an expression index rather than a plain unique constraint,
  which the catalog persistence layer must account for (e.g. duplicate-SKU conflicts
  surface as a constraint violation keyed on the lower-cased value, not the raw value).
- Faceted search, richer per-option metadata, or cross-product shared options would
  require a schema migration away from the flat JSONB attributes map — accepted here as
  future migration cost, not paid now.
- A Category hierarchy or a Collection aggregate, if added later, is additive on top of
  the existing flat many-to-many join table, not a redesign of it.
- Because `catalog` is closed and exposes no public Java API yet, any future module
  (e.g. `inventory` or `ordering`) that needs to read catalog data will require this
  decision to be revisited to add a deliberate public API — it cannot reach into
  `catalog.internal` in the meantime.

## What would cause us to revisit this decision

- A concrete requirement for faceted/filterable storefront search, or for richer
  per-option metadata (display names, ordering, swatches shared across Products) —
  triggers normalizing Variant attributes out of JSONB into option/option-value tables.
- A concrete requirement for nested category taxonomy or curated, manually-ordered
  collections — triggers adding a Category hierarchy or a separate Collection
  aggregate on top of the existing flat many-to-many structure.
- A concrete shipping/fulfillment requirement — triggers adding shipping-dimension
  fields (weight, dimensions) to ProductVariant or a related entity.
- A real cross-module consumer of catalog data (e.g. `inventory` needing
  ProductVariant identifiers, or `storefront` needing product data) — triggers
  designing and adding catalog's public Java API, following the same pattern tenancy
  already established.
- Evidence that the JSONB canonical-form-equality constraint on attribute combinations
  is insufficient in practice (e.g. application-level normalization bugs producing
  distinct JSON for what should be the same attribute set) — would require revisiting
  how attribute identity is enforced.
