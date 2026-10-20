package io.github.abdelrahmankhalil.aicommerce.catalog;

import java.util.UUID;

/**
 * A scoped ProductVariant existence/identity projection for use by other modules
 * (ADR 007) - never the {@code ProductVariant} JPA entity, Product data, SKU, price,
 * attributes, categories, images, or Variant status. An {@code INACTIVE} Variant is
 * still a valid {@code VariantReference} - stockability is not coupled to
 * sellability.
 */
public record VariantReference(UUID id, UUID organizationId, UUID storeId) {
}
