package io.github.abdelrahmankhalil.aicommerce.tenancy.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface StoreRepository extends JpaRepository<Store, UUID> {

    Optional<Store> findBySlug(String slug);

    Optional<Store> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsBySlug(String slug);
}
