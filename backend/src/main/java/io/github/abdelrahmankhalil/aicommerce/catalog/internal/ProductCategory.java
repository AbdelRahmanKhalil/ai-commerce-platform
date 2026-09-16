package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * The many-to-many association row between a Product and a Category (ADR 006).
 * {@code organizationId}/{@code storeId} are denormalized from both sides
 * specifically so the database can enforce, via composite foreign keys (see
 * {@code V2__catalog.sql}), that a Product and a Category linked by this row always
 * belong to the same Organization/Store - the invariant is not left to application
 * code alone.
 */
@Entity
@Table(name = "product_category")
public class ProductCategory {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid", updatable = false)
    private UUID organizationId;

    @Column(name = "store_id", nullable = false, columnDefinition = "uuid", updatable = false)
    private UUID storeId;

    @Column(name = "product_id", nullable = false, columnDefinition = "uuid", updatable = false)
    private UUID productId;

    @Column(name = "category_id", nullable = false, columnDefinition = "uuid", updatable = false)
    private UUID categoryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProductCategory() {
        // JPA
    }

    public ProductCategory(UUID organizationId, UUID storeId, UUID productId, UUID categoryId) {
        this.organizationId = organizationId;
        this.storeId = storeId;
        this.productId = productId;
        this.categoryId = categoryId;
        this.createdAt = Instant.now();
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

    public UUID getProductId() {
        return productId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
