package io.github.abdelrahmankhalil.aicommerce.tenancy;

import java.util.Set;
import java.util.UUID;

/**
 * The tenancy module's authorization API. Every other module resolves "who is calling"
 * and "may they act on this Organization/Store" exclusively through this interface -
 * never by querying tenancy's entities/repositories directly, and never by trusting a
 * client-supplied Organization/Store id without this check.
 * <p>
 * Deliberately depends on no Spring Security type: the web/authentication edge is
 * responsible for validating the JWT and extracting the subject before calling in here.
 */
public interface TenancyAuthorization {

    /**
     * Resolves the local UserAccount id for an already-authenticated external
     * (Keycloak) subject.
     *
     * @throws UserAccountNotProvisionedException if no UserAccount has been
     *                                             provisioned for this subject yet
     */
    UUID resolveUserAccountId(String externalSubject);

    /**
     * Requires that the given UserAccount has at least a Membership in the given
     * Organization (any {@link OrganizationRole}).
     *
     * @throws TenancyAccessDeniedException if the UserAccount has no Membership in
     *                                       this Organization
     */
    void requireOrganizationAccess(UUID userAccountId, UUID organizationId);

    /**
     * Requires that the given UserAccount's Membership in the given Organization has
     * one of the given {@link OrganizationRole}s - for actions that only specific
     * Organization-level roles may take (e.g. creating a Store), regardless of any
     * Store-scoped grant.
     *
     * @throws TenancyAccessDeniedException if there is no Membership, or its role is
     *                                       not in {@code anyOf}
     */
    void requireOrganizationRole(UUID userAccountId, UUID organizationId, Set<OrganizationRole> anyOf);

    /**
     * Requires that the given UserAccount may act on the given Store within the given
     * Organization: either the UserAccount's Membership role is
     * {@link OrganizationRole#OWNER}/{@link OrganizationRole#ADMIN} (organization-wide
     * Store access), or it is a {@link OrganizationRole#MEMBER} holding at least one
     * {@code StoreAccess} grant for this Store whose role is in {@code anyOf}.
     *
     * @throws TenancyAccessDeniedException if neither condition holds, including when
     *                                       the Store does not belong to the given
     *                                       Organization
     */
    void requireStoreAccess(UUID userAccountId, UUID organizationId, UUID storeId, Set<StoreRole> anyOf);
}
