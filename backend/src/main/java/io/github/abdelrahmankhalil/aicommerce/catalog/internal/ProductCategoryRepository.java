package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

    List<ProductCategory> findByProductIdAndStoreIdAndOrganizationId(UUID productId, UUID storeId, UUID organizationId);

    void deleteByProductIdAndStoreIdAndOrganizationId(UUID productId, UUID storeId, UUID organizationId);
}
