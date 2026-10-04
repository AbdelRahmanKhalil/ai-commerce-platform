package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

import java.math.BigDecimal;
import java.util.Map;

/**
 * An initial Variant supplied as part of Product creation - the web layer's own
 * request DTO shape, translated to this internal input before calling
 * {@code ProductService}, so internal application services never depend on
 * {@code catalog.web} request types.
 */
public record VariantInput(String sku, VariantStatus status, BigDecimal price, Map<String, String> attributes) {
}
