package io.github.abdelrahmankhalil.aicommerce.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Minimal, raw-SQL test fixture helpers for tenancy scenarios that Milestone 1's
 * public API deliberately does not expose yet - specifically, adding a plain
 * {@code MEMBER} Membership (there is no "invite a member" endpoint in this slice).
 * Everything else goes through the real public API/HTTP surface, not this class.
 */
public final class TenancyFixtures {

    private TenancyFixtures() {
    }

    public static UUID addMembership(JdbcTemplate jdbcTemplate, UUID userAccountId, UUID organizationId, String role) {
        UUID membershipId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO membership (id, user_account_id, organization_id, role, created_at) VALUES (?, ?, ?, ?, ?)",
                membershipId, userAccountId, organizationId, role, OffsetDateTime.now());
        return membershipId;
    }

    public static long countUserAccountsBySubject(JdbcTemplate jdbcTemplate, String keycloakSubject) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE keycloak_subject = ?", Long.class, keycloakSubject);
        return count == null ? 0 : count;
    }
}
