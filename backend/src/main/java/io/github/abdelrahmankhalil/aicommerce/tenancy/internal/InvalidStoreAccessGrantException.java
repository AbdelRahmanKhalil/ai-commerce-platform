package io.github.abdelrahmankhalil.aicommerce.tenancy.internal;

import java.util.UUID;

/**
 * A StoreAccess grant was requested for a Membership that is already
 * {@code OWNER}/{@code ADMIN} - Organization-wide roles that already imply access to
 * every Store, per {@code OrganizationRole}'s Javadoc. StoreAccess exists specifically
 * to narrow a {@code MEMBER}'s access to particular Stores, so granting it to an
 * OWNER/ADMIN Membership would be a meaningless, stale grant. Maps to HTTP 400.
 * <p>
 * Not part of {@code tenancy}'s public API - see {@link MembershipNotFoundException}
 * for the same reasoning.
 */
public class InvalidStoreAccessGrantException extends RuntimeException {

    public InvalidStoreAccessGrantException(UUID membershipId) {
        super("Membership " + membershipId + " already has Organization-wide Store access "
                + "(OWNER/ADMIN) - StoreAccess grants are only meaningful for MEMBER Memberships");
    }
}
