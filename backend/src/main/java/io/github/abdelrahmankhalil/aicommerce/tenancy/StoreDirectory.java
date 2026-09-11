package io.github.abdelrahmankhalil.aicommerce.tenancy;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only Store lookups for other modules, returning {@link StoreSummary}
 * projections rather than tenancy's entities.
 */
public interface StoreDirectory {

    /**
     * Resolves a Store by its public slug, with no Organization/tenant context
     * required - this is the entry point that public storefront hostname resolution
     * uses to establish which Organization/Store a request belongs to in the first
     * place, so it intentionally is not itself tenant-scoped.
     */
    Optional<StoreSummary> findBySlug(String slug);

    /**
     * Resolves a Store by id, scoped to the given Organization. Returns empty if the
     * Store does not exist, or exists under a different Organization - callers must
     * never learn that a Store id exists under another Organization.
     */
    Optional<StoreSummary> getStore(UUID organizationId, UUID storeId);
}
