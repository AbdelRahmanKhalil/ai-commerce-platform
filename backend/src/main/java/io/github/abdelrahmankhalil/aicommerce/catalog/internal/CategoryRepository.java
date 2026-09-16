package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findByIdAndStoreIdAndOrganizationId(UUID id, UUID storeId, UUID organizationId);

    Page<Category> findByStoreIdAndOrganizationId(UUID storeId, UUID organizationId, Pageable pageable);

    long countByIdInAndStoreIdAndOrganizationId(Collection<UUID> ids, UUID storeId, UUID organizationId);
}
