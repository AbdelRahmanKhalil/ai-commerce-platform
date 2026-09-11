package io.github.abdelrahmankhalil.aicommerce.tenancy.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Optional<Membership> findByUserAccountIdAndOrganizationId(UUID userAccountId, UUID organizationId);
}
