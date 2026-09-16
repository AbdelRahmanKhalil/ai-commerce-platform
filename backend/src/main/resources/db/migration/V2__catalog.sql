-- Catalog module schema (ADR 006). All primary keys are native Postgres uuid columns
-- whose values are always generated application-side (Hibernate's @UuidGenerator),
-- continuing the V1__tenancy.sql convention - no DB-side DEFAULT gen_random_uuid().
--
-- Category/Product/ProductVariant/ProductCategory/ProductImage are all Store-scoped:
-- every row carries both organization_id and store_id, and every child table uses a
-- composite foreign key against its parent's (id, store_id, organization_id) to make
-- cross-Store/cross-Organization references impossible at the database level, mirroring
-- the store_access pattern already established in V1__tenancy.sql.

CREATE TABLE category (
    id              uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    store_id        uuid NOT NULL,
    name            text NOT NULL,
    slug            varchar(200) NOT NULL,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT fk_category_store_organization
        FOREIGN KEY (store_id, organization_id) REFERENCES store (id, organization_id),
    -- Composite-FK target for product_category.
    CONSTRAINT uq_category_id_store_organization UNIQUE (id, store_id, organization_id),
    -- Slug uniqueness is Store-scoped, not global - the same slug may exist in
    -- different Stores.
    CONSTRAINT uq_category_store_slug UNIQUE (store_id, slug),
    -- Normalized slug: lowercase letters/digits/hyphens, no leading/trailing hyphen.
    CONSTRAINT ck_category_slug_format CHECK (slug ~ '^[a-z0-9]([a-z0-9-]{0,198}[a-z0-9])?$')
);

CREATE TABLE product (
    id              uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    store_id        uuid NOT NULL,
    title           text NOT NULL,
    description     text,
    slug            varchar(200) NOT NULL,
    status          varchar(20) NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT fk_product_store_organization
        FOREIGN KEY (store_id, organization_id) REFERENCES store (id, organization_id),
    -- Composite-FK target for product_category/product_variant/product_image.
    CONSTRAINT uq_product_id_store_organization UNIQUE (id, store_id, organization_id),
    -- Slug uniqueness is Store-scoped, not global.
    CONSTRAINT uq_product_store_slug UNIQUE (store_id, slug),
    CONSTRAINT ck_product_slug_format CHECK (slug ~ '^[a-z0-9]([a-z0-9-]{0,198}[a-z0-9])?$')
    -- Products are never hard-deleted in this slice (see ProductStatus lifecycle).
);

CREATE TABLE product_category (
    id              uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    store_id        uuid NOT NULL,
    product_id      uuid NOT NULL,
    category_id     uuid NOT NULL,
    created_at      timestamptz NOT NULL,
    -- A Product/Category pair may only be associated once.
    CONSTRAINT uq_product_category_product_category UNIQUE (product_id, category_id),
    -- The Product referenced by this association must belong to the same
    -- organization_id/store_id carried on the association row itself.
    CONSTRAINT fk_product_category_product
        FOREIGN KEY (product_id, store_id, organization_id) REFERENCES product (id, store_id, organization_id),
    -- The Category referenced by this association must belong to the same
    -- organization_id/store_id carried on the association row itself. Deleting a
    -- Category cascades only these association rows - it never touches product.
    CONSTRAINT fk_product_category_category
        FOREIGN KEY (category_id, store_id, organization_id) REFERENCES category (id, store_id, organization_id)
        ON DELETE CASCADE
);

CREATE TABLE product_variant (
    id              uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    store_id        uuid NOT NULL,
    product_id      uuid NOT NULL,
    sku             varchar(100) NOT NULL,
    status          varchar(20) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    price           numeric(12, 2) NOT NULL,
    attributes      jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT fk_product_variant_product
        FOREIGN KEY (product_id, store_id, organization_id) REFERENCES product (id, store_id, organization_id),
    -- Composite-FK target for a future Inventory consumer (ADR 006).
    CONSTRAINT uq_product_variant_id_store_organization UNIQUE (id, store_id, organization_id),
    CONSTRAINT ck_product_variant_price_non_negative CHECK (price >= 0),
    -- Defensive backstop for the "persisted SKU has no leading/trailing whitespace,
    -- and is non-blank" application invariant.
    CONSTRAINT ck_product_variant_sku_trimmed CHECK (sku = btrim(sku) AND sku <> ''),
    -- attributes must be a flat JSON object (never an array/scalar) at the top level.
    CONSTRAINT ck_product_variant_attributes_is_object CHECK (jsonb_typeof(attributes) = 'object'),
    -- Two Variants of the same Product may not share an identical attribute
    -- combination. jsonb equality is canonical-form (key order and whitespace do not
    -- defeat this constraint).
    CONSTRAINT uq_product_variant_product_attributes UNIQUE (product_id, attributes)
    -- Variant rows are never hard-deleted in this slice (see VariantStatus lifecycle).
);

-- SKU uniqueness is case-insensitive within a Store, preserving the merchant's
-- original casing on the stored row - enforced via an expression index over the
-- lower-cased value rather than a plain unique constraint or citext column.
CREATE UNIQUE INDEX uq_product_variant_store_sku_ci ON product_variant (store_id, lower(sku));

CREATE TABLE product_image (
    id              uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    store_id        uuid NOT NULL,
    product_id      uuid NOT NULL,
    url             text NOT NULL,
    alt_text        text,
    position        integer NOT NULL,
    created_at      timestamptz NOT NULL,
    CONSTRAINT fk_product_image_product
        FOREIGN KEY (product_id, store_id, organization_id) REFERENCES product (id, store_id, organization_id),
    CONSTRAINT ck_product_image_position_non_negative CHECK (position >= 0),
    CONSTRAINT uq_product_image_product_position UNIQUE (product_id, position)
    -- ProductImage is mutable merchandising data - unlike Product/ProductVariant, its
    -- rows may be hard-deleted/replaced (see ADR 006).
);

CREATE INDEX idx_category_store_organization ON category (store_id, organization_id);
CREATE INDEX idx_product_store_organization_created ON product (store_id, organization_id, created_at DESC, id DESC);
CREATE INDEX idx_product_store_organization_status_created ON product (store_id, organization_id, status, created_at DESC, id DESC);
CREATE INDEX idx_product_category_product ON product_category (product_id);
CREATE INDEX idx_product_category_category ON product_category (category_id);
CREATE INDEX idx_product_variant_product ON product_variant (product_id, store_id, organization_id);
CREATE INDEX idx_product_image_product ON product_image (product_id, store_id, organization_id);
