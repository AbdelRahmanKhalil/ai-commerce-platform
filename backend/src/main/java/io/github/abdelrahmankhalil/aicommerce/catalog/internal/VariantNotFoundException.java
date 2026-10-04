package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

import java.util.UUID;

/**
 * No ProductVariant with the given id exists for the requested Product within the
 * requested Store/Organization. A Variant is never loaded/updated by its id alone -
 * every lookup also requires the owning productId, storeId and organizationId to
 * match. Maps to HTTP 404.
 */
public class VariantNotFoundException extends RuntimeException {

    public VariantNotFoundException(UUID variantId) {
        super("ProductVariant " + variantId + " not found");
    }
}
