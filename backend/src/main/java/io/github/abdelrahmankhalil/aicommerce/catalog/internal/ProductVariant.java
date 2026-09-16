package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A Store-scoped ProductVariant (ADR 006). Every Product has at least one Variant -
 * enforced as an application invariant by {@code ProductService}, never by a
 * declarative database constraint. Never hard-deleted -
 * {@link VariantStatus#INACTIVE} is the terminal lifecycle state instead.
 * <p>
 * {@code attributes} is a flat {@code String -> String} map persisted as JSONB
 * (never a normalized option/option-value schema, per ADR 006). Price carries no
 * currency - currency is always resolved from the owning Store via tenancy's public
 * API.
 */
@Entity
@Table(name = "product_variant")
public class ProductVariant {

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

    @Column(nullable = false, length = 100)
    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VariantStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> attributes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductVariant() {
        // JPA
    }

    public ProductVariant(UUID organizationId, UUID storeId, UUID productId, String sku, VariantStatus status,
                           BigDecimal price, Map<String, String> attributes) {
        this.organizationId = organizationId;
        this.storeId = storeId;
        this.productId = productId;
        this.sku = sku;
        this.status = status;
        this.price = price;
        this.attributes = attributes;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String sku, BigDecimal price, Map<String, String> attributes, VariantStatus status) {
        this.sku = sku;
        this.price = price;
        this.attributes = attributes;
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

    public UUID getProductId() {
        return productId;
    }

    public String getSku() {
        return sku;
    }

    public VariantStatus getStatus() {
        return status;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
