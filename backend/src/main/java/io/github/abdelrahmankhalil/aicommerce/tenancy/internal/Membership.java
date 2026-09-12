package io.github.abdelrahmankhalil.aicommerce.tenancy.internal;

import io.github.abdelrahmankhalil.aicommerce.tenancy.OrganizationRole;
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
 * A UserAccount's membership in one Organization, carrying its Organization-level
 * role. Referenced by id from {@link StoreAccess} to narrow a MEMBER's access to
 * specific Stores.
 */
@Entity
@Table(name = "membership")
public class Membership {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_account_id", nullable = false, columnDefinition = "uuid")
    private UUID userAccountId;

    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid")
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrganizationRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Membership() {
        // JPA
    }

    public Membership(UUID userAccountId, UUID organizationId, OrganizationRole role) {
        this.userAccountId = userAccountId;
        this.organizationId = organizationId;
        this.role = role;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserAccountId() {
        return userAccountId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public OrganizationRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
