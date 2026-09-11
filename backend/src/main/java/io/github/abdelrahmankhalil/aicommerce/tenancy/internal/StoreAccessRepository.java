package io.github.abdelrahmankhalil.aicommerce.tenancy.internal;

import io.github.abdelrahmankhalil.aicommerce.tenancy.StoreRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

interface StoreAccessRepository extends JpaRepository<StoreAccess, UUID> {

    boolean existsByMembershipIdAndStoreIdAndRoleIn(UUID membershipId, UUID storeId, Collection<StoreRole> roles);

    boolean existsByMembershipIdAndStoreIdAndRole(UUID membershipId, UUID storeId, StoreRole role);
}
