package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    List<ProductVariant> findByProductIdAndStoreIdAndOrganizationId(UUID productId, UUID storeId, UUID organizationId);

    Optional<ProductVariant> findByIdAndProductIdAndStoreIdAndOrganizationId(UUID id, UUID productId, UUID storeId,
                                                                              UUID organizationId);
}
