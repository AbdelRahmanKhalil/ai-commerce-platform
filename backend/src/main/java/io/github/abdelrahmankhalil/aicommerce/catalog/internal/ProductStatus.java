package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

/**
 * Product lifecycle status (ADR 006). Products are never hard-deleted; ARCHIVED is
 * the terminal state instead. This MVP slice does not implement a state-machine
 * transition policy - moves between any two values are allowed.
 */
public enum ProductStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED
}
