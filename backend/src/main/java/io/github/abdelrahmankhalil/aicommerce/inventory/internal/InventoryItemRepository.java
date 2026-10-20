package io.github.abdelrahmankhalil.aicommerce.inventory.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    /**
     * The only way an InventoryItem is ever looked up at the application boundary -
     * never a bare {@code findById} - so a foreign Store/Organization reference fails
     * safely.
     */
    Optional<InventoryItem> findByProductVariantIdAndStoreIdAndOrganizationId(UUID productVariantId, UUID storeId,
                                                                                UUID organizationId);

    Page<InventoryItem> findByStoreIdAndOrganizationId(UUID storeId, UUID organizationId, Pageable pageable);
}
