package io.github.abdelrahmankhalil.aicommerce.tenancy.internal;

import io.github.abdelrahmankhalil.aicommerce.tenancy.StoreRole;
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
 * One Store-scoped role grant for a MEMBER's Membership. Multiple rows may exist for
 * the same (membership, store) pair, each a different {@link StoreRole}.
 * <p>
 * {@code organizationId} is denormalized from both the owning Membership and the
 * target Store specifically so the database can enforce, via composite foreign keys
 * (see the {@code V1__tenancy.sql} migration), that a grant can never link a
 * Membership in one Organization to a Store in a different Organization - the
 * invariant is not left to application code alone.
 */
@Entity
@Table(name = "store_access")
public class StoreAccess {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "membership_id", nullable = false, columnDefinition = "uuid")
    private UUID membershipId;

    @Column(name = "store_id", nullable = false, columnDefinition = "uuid")
    private UUID storeId;

    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid")
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StoreRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StoreAccess() {
        // JPA
    }

    public StoreAccess(UUID membershipId, UUID storeId, UUID organizationId, StoreRole role) {
        this.membershipId = membershipId;
        this.storeId = storeId;
        this.organizationId = organizationId;
        this.role = role;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMembershipId() {
        return membershipId;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public StoreRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
