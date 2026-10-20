package io.github.abdelrahmankhalil.aicommerce.inventory.internal;

import java.util.UUID;

/**
 * No ProductVariant with the given id exists within the requested Store/Organization
 * scope, per {@code CatalogVariantDirectory}. Covers both a genuinely nonexistent
 * Variant and one that exists under a different Store or Organization - the two are
 * never distinguished, so this never reveals whether the id exists elsewhere. Maps to
 * HTTP 404.
 */
public class InventoryVariantNotFoundException extends RuntimeException {

    public InventoryVariantNotFoundException(UUID variantId) {
        super("ProductVariant " + variantId + " not found");
    }
}
