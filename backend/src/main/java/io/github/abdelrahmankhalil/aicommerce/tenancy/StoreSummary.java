package io.github.abdelrahmankhalil.aicommerce.tenancy;

import java.util.UUID;

/**
 * Read-only projection of a Store for use by other modules - never the JPA entity
 * itself.
 */
public record StoreSummary(
        UUID id,
        UUID organizationId,
        String name,
        String slug,
        String currencyCode) {
}
