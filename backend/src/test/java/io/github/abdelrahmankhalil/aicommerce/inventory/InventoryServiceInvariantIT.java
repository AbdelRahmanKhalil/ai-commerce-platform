package io.github.abdelrahmankhalil.aicommerce.inventory;

import io.github.abdelrahmankhalil.aicommerce.inventory.internal.InvalidInventoryQuantityException;
import io.github.abdelrahmankhalil.aicommerce.inventory.internal.InventoryService;
import io.github.abdelrahmankhalil.aicommerce.tenancy.TenancyAccessDeniedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR 007: non-negative quantity and Store-write authorization are application
 * invariants that {@code InventoryService} itself must enforce at the write
 * transaction boundary - not merely via the controller's {@code @Min} Bean Validation
 * or HTTP security. These tests call {@code InventoryService} directly, bypassing
 * {@code InventoryController}, request DTO Bean Validation, and HTTP security/controller
 * behavior entirely, to prove both guards hold for any caller - not only ones that go
 * through HTTP first - mirroring {@code catalog.ProductServiceInvariantIT}.
 */
class InventoryServiceInvariantIT extends AbstractInventoryIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Test
    void setOnHandQuantityRejectsNegativeQuantityWithoutPersistingAnyRow() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Service Invariant Negative");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID variantId = createVariant(ownerSubject, organizationId, storeId);
        UUID userAccountId = userAccountId(ownerSubject);

        assertThatThrownBy(() -> inventoryService.setOnHandQuantity(organizationId, storeId, userAccountId,
                variantId, -1))
                .isInstanceOf(InvalidInventoryQuantityException.class);

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_item WHERE product_variant_id = ?", Long.class, variantId);
        assertThat(count).isZero();
    }

    @Test
    void setOnHandQuantityRejectsAStoreScopedReadOnlyRoleWithoutPersistingAnyRow() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Service Invariant AuthZ RO");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID variantId = createVariant(ownerSubject, organizationId, storeId);

        String memberSubject = provisionedSubject();
        UUID memberUserAccountId = addMember(memberSubject, organizationId);
        grantStoreAccess(ownerSubject, organizationId, storeId, memberUserAccountId, "CATALOG_EDITOR");

        assertThatThrownBy(() -> inventoryService.setOnHandQuantity(organizationId, storeId, memberUserAccountId,
                variantId, 10))
                .isInstanceOf(TenancyAccessDeniedException.class);

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_item WHERE product_variant_id = ?", Long.class, variantId);
        assertThat(count).isZero();
    }

    @Test
    void setOnHandQuantityRejectsAMemberWithNoStoreAccessAtAllWithoutPersistingAnyRow() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Service Invariant AuthZ None");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID variantId = createVariant(ownerSubject, organizationId, storeId);

        String memberSubject = provisionedSubject();
        UUID memberUserAccountId = addMember(memberSubject, organizationId);

        assertThatThrownBy(() -> inventoryService.setOnHandQuantity(organizationId, storeId, memberUserAccountId,
                variantId, 10))
                .isInstanceOf(TenancyAccessDeniedException.class);

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_item WHERE product_variant_id = ?", Long.class, variantId);
        assertThat(count).isZero();
    }
}
