package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

/**
 * ProductVariant lifecycle status (ADR 006). Variant rows are never hard-deleted;
 * INACTIVE is used instead. A Product may have every one of its Variants INACTIVE -
 * that is not the same as having zero Variants.
 */
public enum VariantStatus {
    ACTIVE,
    INACTIVE
}
