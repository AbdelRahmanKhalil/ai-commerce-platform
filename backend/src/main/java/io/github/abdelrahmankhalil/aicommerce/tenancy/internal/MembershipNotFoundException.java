package io.github.abdelrahmankhalil.aicommerce.tenancy.internal;

import java.util.UUID;

/**
 * The target UserAccount of a requested operation (e.g. granting StoreAccess) is not
 * a Member of the given Organization. Maps to HTTP 400 - the caller asked for
 * something that presupposes a Membership that doesn't exist.
 * <p>
 * Not part of {@code tenancy}'s public API - only thrown by
 * {@link StoreManagementService} and caught by {@code tenancy.web}'s own exception
 * handler, both in this same module. Unlike {@code TenancyAccessDeniedException}/
 * {@code UserAccountNotProvisionedException}, this is not part of
 * {@code TenancyAuthorization}'s public contract, so it does not need to live in the
 * module's public root package.
 */
public class MembershipNotFoundException extends RuntimeException {

    public MembershipNotFoundException(UUID userAccountId, UUID organizationId) {
        super("UserAccount " + userAccountId + " is not a Member of Organization " + organizationId);
    }
}
