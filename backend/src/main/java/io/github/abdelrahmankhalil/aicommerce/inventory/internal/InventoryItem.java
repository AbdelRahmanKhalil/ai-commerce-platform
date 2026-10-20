package io.github.abdelrahmankhalil.aicommerce.inventory.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Exactly one InventoryItem per ProductVariant (ADR 007), with its own
 * application-generated UUID identity - never a reuse of {@code ProductVariant.id}.
 * Store-scoped: {@code organizationId}/{@code storeId} are explicit on every row,
 * continuing ADR 004. No JPA relationship to ProductVariant - {@code
 * productVariantId} is stored explicitly so Inventory never imports Catalog's JPA
 * type.
 * <p>
 * Only {@code onHandQuantity} is stored, as a discrete, non-negative unit count - no
 * reserved/available quantity exists yet (ADR 007). {@code version} backs JPA
 * optimistic locking for staff-side writes to an already-existing row only; it is not
 * a commitment about how a future checkout/reservation workflow will handle
 * concurrency.
 */
@Entity
@Table(name = "inventory_item")
public class InventoryItem {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid", updatable = false)
    private UUID organizationId;

    @Column(name = "store_id", nullable = false, columnDefinition = "uuid", updatable = false)
    private UUID storeId;

    @Column(name = "product_variant_id", nullable = false, columnDefinition = "uuid", updatable = false)
    private UUID productVariantId;

    @Column(name = "on_hand_quantity", nullable = false)
    private long onHandQuantity;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryItem() {
        // JPA
    }

    public InventoryItem(UUID organizationId, UUID storeId, UUID productVariantId, long onHandQuantity) {
        this.organizationId = organizationId;
        this.storeId = storeId;
        this.productVariantId = productVariantId;
        this.onHandQuantity = onHandQuantity;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Absolute replacement - the sole stock-write operation in this slice (ADR 007). */
    public void replaceOnHandQuantity(long onHandQuantity) {
        this.onHandQuantity = onHandQuantity;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public UUID getProductVariantId() {
        return productVariantId;
    }

    public long getOnHandQuantity() {
        return onHandQuantity;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
