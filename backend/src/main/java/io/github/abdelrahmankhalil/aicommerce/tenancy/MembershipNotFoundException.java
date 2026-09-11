package io.github.abdelrahmankhalil.aicommerce.tenancy;

import java.util.UUID;

/**
 * The target UserAccount of a requested operation (e.g. granting StoreAccess) is not
 * a Member of the given Organization. Maps to HTTP 400 - the caller asked for
 * something that presupposes a Membership that doesn't exist.
 */
public class MembershipNotFoundException extends RuntimeException {

    public MembershipNotFoundException(UUID userAccountId, UUID organizationId) {
        super("UserAccount " + userAccountId + " is not a Member of Organization " + organizationId);
    }
}
