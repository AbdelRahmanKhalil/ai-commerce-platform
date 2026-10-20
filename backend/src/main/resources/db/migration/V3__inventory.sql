-- Inventory module schema (ADR 007). InventoryItem owns its own application-generated
-- UUID identity, generated application-side (Hibernate's @UuidGenerator), continuing
-- the V1__tenancy.sql/V2__catalog.sql convention - never a reuse of
-- product_variant.id, and no DB-side DEFAULT gen_random_uuid().
--
-- InventoryItem is Store-scoped: every row carries both organization_id and store_id
-- explicitly. The composite foreign key to product_variant (id, store_id,
-- organization_id) makes a cross-Store or cross-Organization reference relationally
-- impossible, continuing the same composite-FK defense-in-depth pattern already used
-- in V1__tenancy.sql and V2__catalog.sql.

CREATE TABLE inventory_item (
    id                  uuid PRIMARY KEY,
    organization_id     uuid NOT NULL,
    store_id            uuid NOT NULL,
    product_variant_id  uuid NOT NULL,
    on_hand_quantity    bigint NOT NULL,
    version             bigint NOT NULL,
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    CONSTRAINT fk_inventory_item_product_variant
        FOREIGN KEY (product_variant_id, store_id, organization_id)
        REFERENCES product_variant (id, store_id, organization_id),
    -- Exactly one InventoryItem per ProductVariant.
    CONSTRAINT uq_inventory_item_product_variant UNIQUE (product_variant_id),
    -- Composite-FK anchor retained for a future InventoryReservation table (ADR 007),
    -- mirroring the pattern ADR 006 already applied to product_variant for
    -- Inventory's own benefit.
    CONSTRAINT uq_inventory_item_id_store_organization UNIQUE (id, store_id, organization_id),
    -- Quantities are discrete units only and must never be negative - enforced here
    -- as the database backstop alongside the application-layer check.
    CONSTRAINT ck_inventory_item_on_hand_quantity_non_negative CHECK (on_hand_quantity >= 0)
);

-- Supports Store-scoped paginated listing ordered by created_at DESC, id DESC - the
-- only listing query this slice has.
CREATE INDEX idx_inventory_item_store_organization_created
    ON inventory_item (store_id, organization_id, created_at DESC, id DESC);
