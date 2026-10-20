package io.github.abdelrahmankhalil.aicommerce.inventory.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * The result of reading a single Variant's inventory (ADR 007). A missing
 * InventoryItem for an existing ProductVariant is a valid, explicit "uninitialized"
 * state, not an error - distinct from a nonexistent/foreign ProductVariant, which
 * {@code InventoryService} rejects before ever producing this type.
 */
public record InventoryLookup(UUID variantId, boolean initialized, long onHandQuantity, Instant createdAt,
                                Instant updatedAt) {

    static InventoryLookup uninitialized(UUID variantId) {
        return new InventoryLookup(variantId, false, 0L, null, null);
    }

    static InventoryLookup initialized(InventoryItem item) {
        return new InventoryLookup(item.getProductVariantId(), true, item.getOnHandQuantity(), item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
