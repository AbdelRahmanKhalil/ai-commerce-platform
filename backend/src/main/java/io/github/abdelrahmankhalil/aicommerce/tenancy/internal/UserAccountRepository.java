package io.github.abdelrahmankhalil.aicommerce.tenancy.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByKeycloakSubject(String keycloakSubject);

    /**
     * Atomic, race-safe provisioning primitive: never throws on a concurrent duplicate
     * subject, so the surrounding transaction stays healthy either way. Callers must
     * follow up with {@link #findByKeycloakSubject(String)} to get the row - theirs, if
     * they won the race, or the other caller's, if they didn't.
     */
    @Modifying
    @Query(value = """
            INSERT INTO user_account (id, keycloak_subject, created_at)
            VALUES (:id, :keycloakSubject, now())
            ON CONFLICT (keycloak_subject) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("id") UUID id, @Param("keycloakSubject") String keycloakSubject);
}
