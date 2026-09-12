-- Tenancy module schema. All primary keys are native Postgres uuid columns whose
-- values are always generated application-side (Hibernate's @UuidGenerator, or
-- UUID.randomUUID() for the native user_account provisioning insert) - no DB-side
-- DEFAULT gen_random_uuid() is used.

CREATE TABLE organization (
    id          uuid PRIMARY KEY,
    name        text NOT NULL,
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL
);

CREATE TABLE user_account (
    id                  uuid PRIMARY KEY,
    keycloak_subject    text NOT NULL,
    created_at          timestamptz NOT NULL,
    CONSTRAINT uq_user_account_keycloak_subject UNIQUE (keycloak_subject)
);

CREATE TABLE membership (
    id               uuid PRIMARY KEY,
    user_account_id  uuid NOT NULL REFERENCES user_account (id),
    organization_id  uuid NOT NULL REFERENCES organization (id),
    role             varchar(20) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    created_at       timestamptz NOT NULL,
    CONSTRAINT uq_membership_user_account_organization UNIQUE (user_account_id, organization_id),
    -- Composite-FK target for store_access: lets the database itself enforce that a
    -- StoreAccess grant's membership_id and organization_id are mutually consistent.
    CONSTRAINT uq_membership_id_organization UNIQUE (id, organization_id)
);

CREATE TABLE store (
    id              uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organization (id),
    name            text NOT NULL,
    slug            varchar(63) NOT NULL,
    currency_code   varchar(3) NOT NULL,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT uq_store_slug UNIQUE (slug),
    -- Store.slug will eventually be used as a DNS subdomain label
    -- ({slug}.raqana.app): lowercase letters/digits/hyphens only, no leading or
    -- trailing hyphen, 1-63 characters (the DNS label limit).
    CONSTRAINT ck_store_slug_dns_label CHECK (slug ~ '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$'),
    CONSTRAINT ck_store_currency_code CHECK (currency_code ~ '^[A-Z]{3}$'),
    -- Composite-FK target for store_access.
    CONSTRAINT uq_store_id_organization UNIQUE (id, organization_id)
);

CREATE TABLE store_access (
    id               uuid PRIMARY KEY,
    membership_id    uuid NOT NULL,
    store_id         uuid NOT NULL,
    organization_id  uuid NOT NULL,
    role             varchar(20) NOT NULL CHECK (role IN ('MANAGER', 'CATALOG_EDITOR', 'ORDER_MANAGER', 'SUPPORT_AGENT')),
    created_at       timestamptz NOT NULL,
    CONSTRAINT uq_store_access_membership_store_role UNIQUE (membership_id, store_id, role),
    -- The Membership <-> Store Organization invariant, enforced by the schema itself:
    -- a grant's (membership_id, organization_id) must reference a real membership row
    -- in that same Organization, and its (store_id, organization_id) must reference a
    -- real store row in that same Organization. A grant that tried to associate a
    -- Membership in Organization A with a Store in Organization B would have to supply
    -- one organization_id value that satisfies both constraints, which is impossible
    -- unless A = B.
    CONSTRAINT fk_store_access_membership_organization
        FOREIGN KEY (membership_id, organization_id) REFERENCES membership (id, organization_id),
    CONSTRAINT fk_store_access_store_organization
        FOREIGN KEY (store_id, organization_id) REFERENCES store (id, organization_id)
);

CREATE INDEX idx_membership_user_account ON membership (user_account_id);
CREATE INDEX idx_store_organization ON store (organization_id);
CREATE INDEX idx_store_access_membership_store ON store_access (membership_id, store_id);
