package io.github.abdelrahmankhalil.aicommerce.tenancy.internal;

import io.github.abdelrahmankhalil.aicommerce.tenancy.StoreDirectory;
import io.github.abdelrahmankhalil.aicommerce.tenancy.StoreSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Single responsibility: read-only Store projections for other modules.
 */
@Service
class StoreDirectoryService implements StoreDirectory {

    private final StoreRepository storeRepository;

    StoreDirectoryService(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoreSummary> findBySlug(String slug) {
        return storeRepository.findBySlug(slug).map(StoreDirectoryService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoreSummary> getStore(UUID organizationId, UUID storeId) {
        return storeRepository.findByIdAndOrganizationId(storeId, organizationId).map(StoreDirectoryService::toSummary);
    }

    private static StoreSummary toSummary(Store store) {
        return new StoreSummary(store.getId(), store.getOrganizationId(), store.getName(), store.getSlug(),
                store.getCurrencyCode());
    }
}
