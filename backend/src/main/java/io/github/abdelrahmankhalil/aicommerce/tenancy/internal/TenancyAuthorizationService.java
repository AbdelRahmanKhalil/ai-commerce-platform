package io.github.abdelrahmankhalil.aicommerce.tenancy.internal;

import io.github.abdelrahmankhalil.aicommerce.tenancy.OrganizationRole;
import io.github.abdelrahmankhalil.aicommerce.tenancy.StoreRole;
import io.github.abdelrahmankhalil.aicommerce.tenancy.TenancyAccessDeniedException;
import io.github.abdelrahmankhalil.aicommerce.tenancy.TenancyAuthorization;
import io.github.abdelrahmankhalil.aicommerce.tenancy.UserAccountNotProvisionedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Single responsibility: the module's authorization decisions. Read-only; never
 * creates or mutates anything.
 */
@Service
class TenancyAuthorizationService implements TenancyAuthorization {

    private final UserAccountRepository userAccountRepository;
    private final MembershipRepository membershipRepository;
    private final StoreRepository storeRepository;
    private final StoreAccessRepository storeAccessRepository;

    TenancyAuthorizationService(UserAccountRepository userAccountRepository,
                                 MembershipRepository membershipRepository,
                                 StoreRepository storeRepository,
                                 StoreAccessRepository storeAccessRepository) {
        this.userAccountRepository = userAccountRepository;
        this.membershipRepository = membershipRepository;
        this.storeRepository = storeRepository;
        this.storeAccessRepository = storeAccessRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UUID resolveUserAccountId(String externalSubject) {
        return userAccountRepository.findByKeycloakSubject(externalSubject)
                .map(UserAccount::getId)
                .orElseThrow(() -> new UserAccountNotProvisionedException(externalSubject));
    }

    @Override
    @Transactional(readOnly = true)
    public void requireOrganizationAccess(UUID userAccountId, UUID organizationId) {
        membershipRepository.findByUserAccountIdAndOrganizationId(userAccountId, organizationId)
                .orElseThrow(() -> new TenancyAccessDeniedException(
                        "UserAccount " + userAccountId + " has no Membership in Organization " + organizationId));
    }

    @Override
    @Transactional(readOnly = true)
    public void requireOrganizationRole(UUID userAccountId, UUID organizationId, Set<OrganizationRole> anyOf) {
        Membership membership = membershipRepository.findByUserAccountIdAndOrganizationId(userAccountId, organizationId)
                .orElseThrow(() -> new TenancyAccessDeniedException(
                        "UserAccount " + userAccountId + " has no Membership in Organization " + organizationId));
        if (!anyOf.contains(membership.getRole())) {
            throw new TenancyAccessDeniedException(
                    "UserAccount " + userAccountId + " does not hold one of the required roles " + anyOf
                            + " in Organization " + organizationId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void requireStoreAccess(UUID userAccountId, UUID organizationId, UUID storeId, Set<StoreRole> anyOf) {
        Membership membership = membershipRepository.findByUserAccountIdAndOrganizationId(userAccountId, organizationId)
                .orElseThrow(() -> new TenancyAccessDeniedException(
                        "UserAccount " + userAccountId + " has no Membership in Organization " + organizationId));

        // The Store must genuinely belong to the requested Organization - a Store id
        // from another Organization must never be distinguishable from a
        // non-existent one.
        storeRepository.findByIdAndOrganizationId(storeId, organizationId)
                .orElseThrow(() -> new TenancyAccessDeniedException(
                        "Store " + storeId + " does not belong to Organization " + organizationId));

        if (membership.getRole() == OrganizationRole.OWNER || membership.getRole() == OrganizationRole.ADMIN) {
            return;
        }

        boolean hasMatchingGrant = storeAccessRepository
                .existsByMembershipIdAndStoreIdAndRoleIn(membership.getId(), storeId, anyOf);
        if (!hasMatchingGrant) {
            throw new TenancyAccessDeniedException(
                    "UserAccount " + userAccountId + " has no StoreAccess grant matching " + anyOf
                            + " for Store " + storeId);
        }
    }
}
