package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * An ordered child image of a Product (ADR 006). Ordinary mutable merchandising data -
 * unlike Product/ProductVariant, rows may be hard-deleted/replaced. Client APIs never
 * accept a {@code position} field directly; array order at the API boundary
 * determines the stored {@code position} (0-based).
 */
@Entity
@Table(name = "product_image")
public class ProductImage {

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

    @Column(nullable = false)
    private String url;

    @Column(name = "alt_text")
    private String altText;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProductImage() {
        // JPA
    }

    public ProductImage(UUID organizationId, UUID storeId, UUID productId, String url, String altText, int position) {
        this.organizationId = organizationId;
        this.storeId = storeId;
        this.productId = productId;
        this.url = url;
        this.altText = altText;
        this.position = position;
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

    public String getUrl() {
        return url;
    }

    public String getAltText() {
        return altText;
    }

    public int getPosition() {
        return position;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
