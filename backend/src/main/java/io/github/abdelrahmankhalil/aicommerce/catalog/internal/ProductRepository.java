package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndStoreIdAndOrganizationId(UUID id, UUID storeId, UUID organizationId);

    Page<Product> findByStoreIdAndOrganizationId(UUID storeId, UUID organizationId, Pageable pageable);

    Page<Product> findByStoreIdAndOrganizationIdAndStatus(UUID storeId, UUID organizationId, ProductStatus status,
                                                            Pageable pageable);
}
