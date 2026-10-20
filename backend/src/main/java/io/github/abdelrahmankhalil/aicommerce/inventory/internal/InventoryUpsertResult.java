package io.github.abdelrahmankhalil.aicommerce.inventory.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * The result of {@code InventoryService.setOnHandQuantity} - whether this write
 * created the InventoryItem (its first initialization, HTTP 201) or replaced the
 * quantity of an already-existing one (HTTP 200).
 */
public record InventoryUpsertResult(boolean created, UUID variantId, long onHandQuantity, Instant createdAt,
                                      Instant updatedAt) {

    static InventoryUpsertResult created(InventoryItem item) {
        return new InventoryUpsertResult(true, item.getProductVariantId(), item.getOnHandQuantity(),
                item.getCreatedAt(), item.getUpdatedAt());
    }

    static InventoryUpsertResult updated(InventoryItem item) {
        return new InventoryUpsertResult(false, item.getProductVariantId(), item.getOnHandQuantity(),
                item.getCreatedAt(), item.getUpdatedAt());
    }
}
