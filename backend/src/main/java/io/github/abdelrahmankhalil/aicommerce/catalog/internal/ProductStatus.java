package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

/**
 * Product lifecycle status (ADR 006). Products are never hard-deleted; status moves
 * between DRAFT/ACTIVE/ARCHIVED instead. This MVP slice does not implement a
 * state-machine transition policy - moves between any two values are allowed,
 * including out of ARCHIVED.
 */
public enum ProductStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED
}
