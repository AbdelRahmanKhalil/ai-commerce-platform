package io.github.abdelrahmankhalil.aicommerce.inventory.internal;

import io.github.abdelrahmankhalil.aicommerce.tenancy.StoreRole;

import java.util.EnumSet;
import java.util.Set;

/**
 * The Store-scoped {@link StoreRole} sets Inventory read/write operations are checked
 * against via {@code TenancyAuthorization.requireStoreAccess}. Organization-wide
 * OWNER/ADMIN access is handled entirely by tenancy itself - Inventory never
 * re-implements that logic here (ADR 007). No {@code INVENTORY_MANAGER} role is
 * added; {@code CATALOG_EDITOR} and {@code SUPPORT_AGENT} are read-only.
 */
final class InventoryRoles {

    static final Set<StoreRole> WRITE = Set.of(StoreRole.MANAGER, StoreRole.ORDER_MANAGER);
    static final Set<StoreRole> READ = EnumSet.allOf(StoreRole.class);

    private InventoryRoles() {
    }
}
