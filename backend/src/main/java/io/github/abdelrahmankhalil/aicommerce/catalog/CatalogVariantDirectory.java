package io.github.abdelrahmankhalil.aicommerce.catalog;

import java.util.Optional;
import java.util.UUID;

/**
 * Catalog's first intentionally public cross-module Java contract (ADR 007),
 * exposing only scoped ProductVariant existence/identity - never the
 * {@code ProductVariant} JPA entity, repository, Product data, SKU, price,
 * attributes, categories, images, or Variant status. An {@code INACTIVE} Variant may
 * legally hold or receive inventory; stockability is not coupled to sellability.
 */
public interface CatalogVariantDirectory {

    /**
     * Resolves a ProductVariant by id, scoped to the given Store and Organization.
     * Returns empty uniformly for "doesn't exist" and "exists in another scope" -
     * callers must never learn that a Variant id exists under another Store or
     * Organization, matching the non-leakage contract {@code StoreDirectory} already
     * establishes.
     */
    Optional<VariantReference> findVariant(UUID organizationId, UUID storeId, UUID variantId);
}
