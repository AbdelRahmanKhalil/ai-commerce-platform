/**
 * Inventory module: {@code InventoryItem}, Store-scoped per ADR 007. This module is
 * closed and exposes no cross-module Java API in this slice; the root package
 * intentionally holds only this module declaration. Implementation types (entities,
 * repositories, application services) live under {@code inventory.internal}, and the
 * HTTP surface lives under {@code inventory.web}. Inventory depends only on {@code
 * tenancy}'s and {@code catalog}'s public (root-package) APIs - never {@code
 * tenancy.internal} or {@code catalog.internal}, tenancy/catalog JPA entities, or
 * their repositories.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Inventory")
package io.github.abdelrahmankhalil.aicommerce.inventory;
