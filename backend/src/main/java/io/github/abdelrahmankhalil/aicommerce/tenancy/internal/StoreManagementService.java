package io.github.abdelrahmankhalil.aicommerce.tenancy.internal;

import io.github.abdelrahmankhalil.aicommerce.tenancy.MembershipNotFoundException;
import io.github.abdelrahmankhalil.aicommerce.tenancy.OrganizationRole;
import io.github.abdelrahmankhalil.aicommerce.tenancy.StoreRole;
import io.github.abdelrahmankhalil.aicommerce.tenancy.TenancyAccessDeniedException;
import io.github.abdelrahmankhalil.aicommerce.tenancy.TenancyAuthorization;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Single responsibility: Store lifecycle and StoreAccess grants. Both operations are
 * restricted to Organization {@code OWNER}/{@code ADMIN} and both re-verify, via
 * explicitly Organization-scoped lookups, that every id involved genuinely belongs to
 * the target Organization - the application-level half of the Membership/Store
 * Organization invariant enforced at the schema level by composite foreign keys on
 * {@code store_access} (see {@code V1__tenancy.sql}).
 */
@Service
public class StoreManagementService {

    private static final Set<OrganizationRole> OWNER_OR_ADMIN = Set.of(OrganizationRole.OWNER, OrganizationRole.ADMIN);

    private final StoreRepository storeRepository;
    private final MembershipRepository membershipRepository;
    private final StoreAccessRepository storeAccessRepository;
    private final TenancyAuthorization tenancyAuthorization;

    StoreManagementService(StoreRepository storeRepository,
                            MembershipRepository membershipRepository,
                            StoreAccessRepository storeAccessRepository,
                            TenancyAuthorization tenancyAuthorization) {
        this.storeRepository = storeRepository;
        this.membershipRepository = membershipRepository;
        this.storeAccessRepository = storeAccessRepository;
        this.tenancyAuthorization = tenancyAuthorization;
    }

    @Transactional
    public UUID createStore(UUID organizationId, UUID requestingUserAccountId, String name, String slug, String currencyCode) {
        tenancyAuthorization.requireOrganizationRole(requestingUserAccountId, organizationId, OWNER_OR_ADMIN);
        Store store = storeRepository.save(new Store(organizationId, name, slug, currencyCode));
        return store.getId();
    }

    @Transactional
    public UUID grantStoreAccess(UUID organizationId, UUID requestingUserAccountId, UUID storeId, UUID targetUserAccountId,
                          StoreRole role) {
        tenancyAuthorization.requireOrganizationRole(requestingUserAccountId, organizationId, OWNER_OR_ADMIN);

        Store store = storeRepository.findByIdAndOrganizationId(storeId, organizationId)
                .orElseThrow(() -> new TenancyAccessDeniedException(
                        "Store " + storeId + " does not belong to Organization " + organizationId));

        Membership targetMembership = membershipRepository.findByUserAccountIdAndOrganizationId(targetUserAccountId, organizationId)
                .orElseThrow(() -> new MembershipNotFoundException(targetUserAccountId, organizationId));

        StoreAccess grant = storeAccessRepository.save(
                new StoreAccess(targetMembership.getId(), store.getId(), organizationId, role));
        return grant.getId();
    }
}
