package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * A flat, Store-scoped tag a Product may belong to (ADR 006) - no hierarchy, no
 * separate Collection aggregate. Ordinary mutable row: may be hard-deleted, which
 * cascades only its {@link ProductCategory} association rows, never the Products
 * themselves.
 */
@Entity
@Table(name = "category")
public class Category {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid", updatable = false)
    private UUID organizationId;

    @Column(name = "store_id", nullable = false, columnDefinition = "uuid", updatable = false)
    private UUID storeId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 200)
    private String slug;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Category() {
        // JPA
    }

    public Category(UUID organizationId, UUID storeId, String name, String slug) {
        this.organizationId = organizationId;
        this.storeId = storeId;
        this.name = name;
        this.slug = slug;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void rename(String name, String slug) {
        this.name = name;
        this.slug = slug;
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

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
