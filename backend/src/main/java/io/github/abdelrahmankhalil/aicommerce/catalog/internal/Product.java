package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * A Store-scoped Product (ADR 006). Never hard-deleted - {@link ProductStatus#ARCHIVED}
 * is the terminal lifecycle state instead. Deliberately has no JPA relations to its
 * Variants/Categories/Images: those are queried explicitly and scoped by
 * organizationId/storeId/productId at each call site, per ADR 004's explicit-scoping
 * requirement and to avoid large, implicitly-loaded object graphs.
 */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid", updatable = false)
    private UUID organizationId;

    @Column(name = "store_id", nullable = false, columnDefinition = "uuid", updatable = false)
    private UUID storeId;

    @Column(nullable = false)
    private String title;

    @Column
    private String description;

    @Column(nullable = false, length = 200)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
        // JPA
    }

    public Product(UUID organizationId, UUID storeId, String title, String description, String slug,
                    ProductStatus status) {
        this.organizationId = organizationId;
        this.storeId = storeId;
        this.title = title;
        this.description = description;
        this.slug = slug;
        this.status = status;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateDetails(String title, String description, String slug, ProductStatus status) {
        this.title = title;
        this.description = description;
        this.slug = slug;
        this.status = status;
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

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getSlug() {
        return slug;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
