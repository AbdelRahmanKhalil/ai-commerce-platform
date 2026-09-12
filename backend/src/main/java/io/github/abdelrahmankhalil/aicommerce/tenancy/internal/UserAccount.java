package io.github.abdelrahmankhalil.aicommerce.tenancy.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "keycloak_subject", nullable = false, unique = true)
    private String keycloakSubject;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserAccount() {
        // JPA
    }

    public UserAccount(String keycloakSubject) {
        this.keycloakSubject = keycloakSubject;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getKeycloakSubject() {
        return keycloakSubject;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
