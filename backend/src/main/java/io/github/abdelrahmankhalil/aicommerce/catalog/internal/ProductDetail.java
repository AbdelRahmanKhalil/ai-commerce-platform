package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

import java.util.List;
import java.util.UUID;

/**
 * The full read model for a single Product: its own fields plus its Variants,
 * ordered Images, and associated Category ids - assembled from four explicitly
 * Store/Organization-scoped queries rather than an eagerly-loaded JPA object graph.
 */
public record ProductDetail(Product product, List<ProductVariant> variants, List<ProductImage> images,
                             List<UUID> categoryIds) {
}
