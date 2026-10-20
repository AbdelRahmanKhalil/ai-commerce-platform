# 007. Inventory model and concurrency

Status: Accepted

## Context

The `catalog` module (see [ADR 006](./006-catalog-domain-model.md)) is complete and
merged; `inventory` is the next module. `ProductVariant` is the sellable catalog unit,
and ADR 006 deliberately added `UNIQUE (id, store_id, organization_id)` to
`product_variant` anticipating Inventory as its next concrete consumer. ADR 006 also
stated that `catalog` exposed no cross-module Java API "in this slice" and explicitly
named its own revisit trigger: "a real cross-module consumer of catalog data (e.g.
`inventory` needing ProductVariant identifiers) ... triggers designing and adding
catalog's public Java API." Inventory is now that trigger firing.

`CLAUDE.md` requires every tenant-scoped operation to prove access explicitly rather
than trusting a client-supplied identifier, forbids an implicit/thread-local tenant
context, warns against infrastructure (Kafka, Redis, Saga, transactional outbox,
distributed locks) introduced without a concrete requirement, and requires automated
tests for concurrency- and tenant-isolation-sensitive behavior. We need to decide the
Inventory aggregate and its identity, what quantity data is actually stored, what a
missing inventory record means, the concurrency guarantee for staff-side stock writes,
how Inventory establishes that a ProductVariant exists in its Store/Organization
without reaching into `catalog.internal`, and which related concerns (reservation,
adjustment history) are deliberately deferred.

## Decision

- **`inventory` is a new, closed Spring Modulith module that exposes no cross-module
  Java API in this slice.** It depends only on `tenancy`'s and `catalog`'s public
  (root-package) APIs, never on `tenancy.internal` or `catalog.internal`. No public
  Inventory Java API is introduced yet because no concrete consumer (storefront,
  ordering) exists.
- **Exactly one `InventoryItem` per `ProductVariant`, with its own
  application-generated UUID identity** — not a reuse of `ProductVariant.id`.
  `organizationId` and `storeId` remain explicit on every row, continuing ADR 004. A
  composite foreign key to `product_variant (id, store_id, organization_id)` makes a
  cross-Store or cross-Organization reference relationally impossible, continuing the
  same composite-FK defense-in-depth pattern already used in `V1__tenancy.sql` and
  `V2__catalog.sql`. `UNIQUE (product_variant_id)` enforces one row per Variant.
  `UNIQUE (id, store_id, organization_id)` is retained as a low-cost future
  composite-FK anchor for an eventual `InventoryReservation` table, mirroring the
  pattern ADR 006 already applied to `product_variant` for Inventory's own benefit.
- **Only `onHandQuantity` is stored, as PostgreSQL `BIGINT` / Java `long`.** Quantities
  are discrete units only (no fractional inventory) and must never be negative,
  enforced at both the database (check constraint) and the application layer. No
  warehouses or locations are modeled.
- **A missing `InventoryItem` for an existing `ProductVariant` is a valid, explicit
  "uninitialized" state, not an error.** Reading such a Variant's inventory returns
  `initialized = false, onHandQuantity = 0`. This is distinct from a nonexistent or
  foreign `ProductVariant`, which is a different, rejected case.
- **A single absolute-replacement upsert operation is the sole stock-write path:** the
  first write for a Variant creates its `InventoryItem`; a later write replaces its
  quantity. No signed-delta/adjustment endpoint and no `InventoryAdjustment`/
  `InventoryHistory` table exist in this slice — absolute replacement is chosen partly
  because it avoids introducing non-idempotent delta semantics and an
  adjustment/idempotency ledger without a current concrete requirement.
- **Concurrency for writes to an existing `InventoryItem` is handled by JPA optimistic
  locking (`@Version`).** This guarantees only that two overlapping writes to the same
  existing row, both having loaded the same version, cannot both commit silently: if A
  loads version 1, B loads version 1, A commits, then B's commit fails as an
  optimistic-locking conflict, mapped to HTTP 409. This does **not** guarantee
  detection of staleness across a separate read and a later write (e.g. staff reads
  stock, time passes, someone else changes it, the first staff member's write still
  targets the now-stale value they originally saw) — no ETag, `If-Match`,
  client-supplied `expectedVersion`, or other broader concurrency protocol is
  introduced in this milestone. `@Version` is scoped to CURRENT staff-side writes only;
  it is **not** a commitment about how a future checkout/reservation workflow will
  handle concurrency — that workflow may retain optimistic locking or justify atomic
  conditional SQL updates, pessimistic locking, or another narrowly-scoped strategy,
  decided when it actually exists.
- **Concurrent first-time initialization of the same ProductVariant's `InventoryItem`
  is arbitrated by the `UNIQUE (product_variant_id)` database constraint, not by
  `@Version`.** Both concurrent requests may observe no existing row; one creation
  succeeds, the other violates the unique constraint and must be surfaced as a clean
  HTTP 409, never a raw 500.
- **No overselling guarantee is claimed by this milestone.** There is no sell, reserve,
  or decrement-for-sale operation yet for that invariant to attach to.
  `InventoryReservation` remains deferred: no Cart/Order consumer exists yet, and
  introducing it now would be speculative. The current schema allows a future
  reservation table to reference `InventoryItem` using its existing scoped identity
  (`id, store_id, organization_id`) without a destructive redesign. When
  Ordering/Checkout exists, that milestone decides how reserved and available quantity
  are represented — derived from reservation rows at read time, maintained as an
  aggregate counter, or another concurrency-safe design — and this ADR does not choose
  that representation now. The conceptual business rule
  `available = onHand - active reservations` is recorded here only as future
  direction, not as a column, field, or API contract that exists today; the current
  staff-facing data exposes only `onHandQuantity`.
- **Authorization follows the existing Organization/Store role model (ADR 002); no new
  role is introduced.** OWNER/ADMIN are Organization-wide via `TenancyAuthorization`,
  unchanged. Among Store roles: `MANAGER` and `ORDER_MANAGER` may read and write
  inventory; `CATALOG_EDITOR` and `SUPPORT_AGENT` are read-only. No
  `INVENTORY_MANAGER` role is added.
- **Catalog gains its first intentionally public cross-module Java contract, because
  Inventory is the concrete consumer ADR 006 explicitly anticipated as its own revisit
  trigger.** The new contract, in catalog's root package:

  ```
  CatalogVariantDirectory.findVariant(UUID organizationId, UUID storeId, UUID variantId)
      -> Optional<VariantReference>

  VariantReference { id, organizationId, storeId }
  ```

  This exposes only scoped Variant existence/identity — never the `ProductVariant` JPA
  entity, repositories, Product data, SKU, price, attributes, categories, images, or
  Variant status. An `INACTIVE` Variant may legally hold or receive inventory;
  stockability is not coupled to sellability. `findVariant` returns `Optional.empty()`
  uniformly for "doesn't exist" and "exists in another scope," matching the
  non-leakage contract `StoreDirectory` already establishes. ADR 006 itself is not
  modified by this decision — it remains historically accurate that no public API
  existed when Catalog was built; this ADR records the anticipated trigger firing.
- **`V3__inventory.sql` is the Inventory migration; `V1__tenancy.sql` and
  `V2__catalog.sql` remain immutable.**
- **Real PostgreSQL/Testcontainers concurrency tests are required**, proving the
  `@Version` conflict and the concurrent-first-initialization conflict by forcing both
  transactions to observe identical pre-commit state (e.g. via
  `CyclicBarrier`/`CountDownLatch`) before either commits — a test that merely issues
  two requests without proving overlap does not satisfy this.
- **Storefront, Cart, Ordering, AI/RAG, and any audit/adjustment-history table remain
  out of scope** for this slice.

## Alternatives considered

- **Pessimistic locking (`SELECT ... FOR UPDATE`).** Rejected for this milestone:
  unjustified overhead for the current low-contention, single-staff-writer workload.
- **A signed delta/adjustment API (e.g. a quantity-change request carrying a positive
  or negative delta).** Rejected: not naturally idempotent, and would require an
  audit/idempotency ledger with no current concrete requirement.
- **Auto-creating `InventoryItem` when a `ProductVariant` is created (direct call or
  domain event from Catalog).** Rejected: inverts the `inventory -> catalog` dependency
  direction for no concrete current benefit.
- **Reusing `ProductVariant.id` as `InventoryItem`'s primary key.** Rejected: ties
  Inventory's row identity to a value owned by another module.
- **HTTP `ETag`/`If-Match`/client-supplied `expectedVersion`, or another general
  concurrency-token protocol.** Rejected for this milestone: broader staleness
  protection than any current staff workflow requires; `@Version` already provides the
  guarantee this milestone actually needs.
- **Implementing `InventoryReservation` now.** Rejected: no concrete Cart/Order
  consumer exists yet; would be speculative architecture ahead of a real requirement.
- **Adding an audit/history table now.** Rejected: no current reconciliation,
  traceability, or non-idempotent-integration requirement identified.
- **Pre-selecting a representation for reserved/available quantity (e.g. committing
  now to a maintained `reserved_quantity` counter column).** Rejected: the real
  consumer (Ordering/Checkout) does not exist yet, and its concurrency and workflow
  requirements will determine whether a derived value, a maintained counter, or another
  design is correct; deciding this now would risk a redesign once that consumer is
  real.

## Consequences

- Catalog now has a minimal, intentional public surface; any future module needing
  more than scoped Variant existence/identity extends `VariantReference` rather than
  bypassing catalog's module boundary.
- Staff clients must handle HTTP 409 by reloading and retrying; the system makes no
  claim about protecting a stale read from being followed by a conflicting write that
  itself doesn't collide with another write, and makes no overselling guarantee.
- `InventoryItem.version` exists for, and is scoped to, the current staff-write
  concurrency guarantee only; it carries no implied guarantee about how a future
  checkout/reservation workflow will be built.

## What would cause us to revisit this decision

- Cart/Ordering introduces a real reservation consumer — triggers adding
  `InventoryReservation` and deciding, at that time, how reserved and available
  quantity are represented (derived, maintained counter, or another design); not
  predetermined by this ADR.
- Introducing `InventoryReservation` requires revisiting the current
  absolute-replacement stock-write semantics, because outstanding reservations create
  an invariant between physical on-hand stock and reserved stock — for example, a
  future write must not be allowed to casually drop on-hand quantity below the active
  reserved quantity if doing so would produce negative available stock. This ADR does
  not solve that problem or design the reservation algorithm; it only records that the
  current unconditional-replacement write cannot simply remain as-is once reservations
  exist.
- Stock-correction volume, or a reconciliation/traceability requirement — triggers
  adding an audit/history table.
- A real external integration requiring delta-style or idempotent stock sync —
  triggers reconsidering a signed-adjustment API with an idempotency key.
- A future checkout workflow's actual contention profile exceeds what optimistic
  locking handles comfortably — that future workflow chooses its own concurrency
  strategy (retaining `@Version`, or adopting atomic conditional updates, pessimistic
  locking, or another approach); not predetermined here.
