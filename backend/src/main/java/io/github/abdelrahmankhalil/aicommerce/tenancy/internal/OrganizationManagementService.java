package io.github.abdelrahmankhalil.aicommerce.tenancy.internal;

import io.github.abdelrahmankhalil.aicommerce.tenancy.OrganizationRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Single responsibility: Organization lifecycle. Creating an Organization always
 * atomically creates the calling UserAccount's owning Membership - there is no
 * Organization without an OWNER.
 */
@Service
public class OrganizationManagementService {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;

    OrganizationManagementService(OrganizationRepository organizationRepository,
                                   MembershipRepository membershipRepository) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional
    public UUID createOrganization(String name, UUID ownerUserAccountId) {
        Organization organization = organizationRepository.save(new Organization(name));
        membershipRepository.save(new Membership(ownerUserAccountId, organization.getId(), OrganizationRole.OWNER));
        return organization.getId();
    }
}
