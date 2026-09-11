package io.github.abdelrahmankhalil.aicommerce.tenancy;

/**
 * A UserAccount's role within a Membership to one Organization.
 * {@link #OWNER} and {@link #ADMIN} imply access to every Store in the Organization;
 * {@link #MEMBER} gains Store access only through explicit {@code StoreAccess} grants.
 */
public enum OrganizationRole {
    OWNER,
    ADMIN,
    MEMBER
}
