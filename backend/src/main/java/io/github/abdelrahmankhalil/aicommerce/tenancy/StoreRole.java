package io.github.abdelrahmankhalil.aicommerce.tenancy;

/**
 * A Store-scoped permission grant, held via {@code StoreAccess} by a {@code MEMBER}
 * Membership. A Membership may hold several of these for the same Store at once.
 * {@link #MANAGER} represents broad Store permissions; callers that would otherwise
 * accept a narrower role for a given action should include {@code MANAGER} in the
 * allowed role set explicitly.
 */
public enum StoreRole {
    MANAGER,
    CATALOG_EDITOR,
    ORDER_MANAGER,
    SUPPORT_AGENT
}
