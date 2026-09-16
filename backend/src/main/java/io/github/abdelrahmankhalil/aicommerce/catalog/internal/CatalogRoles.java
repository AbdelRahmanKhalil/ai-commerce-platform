package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

import io.github.abdelrahmankhalil.aicommerce.tenancy.StoreRole;

import java.util.EnumSet;
import java.util.Set;

/**
 * The Store-scoped {@link StoreRole} sets catalog write/read operations are checked
 * against via {@code TenancyAuthorization.requireStoreAccess}. Organization-wide
 * OWNER/ADMIN access is handled entirely by tenancy itself - catalog never
 * re-implements that logic here (ADR 006).
 */
final class CatalogRoles {

    static final Set<StoreRole> WRITE = Set.of(StoreRole.MANAGER, StoreRole.CATALOG_EDITOR);
    static final Set<StoreRole> READ = EnumSet.allOf(StoreRole.class);

    private CatalogRoles() {
    }
}
