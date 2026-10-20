package io.github.abdelrahmankhalil.aicommerce.inventory;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The MISSING INVENTORY cases required by the task brief (ADR 007): a missing
 * InventoryItem for an existing Variant is a valid "uninitialized" read, not an
 * error; a nonexistent/foreign Variant is a 404; reading never creates a row.
 */
class InventoryMissingItemIT extends AbstractInventoryIntegrationTest {

    @Test
    void existingVariantWithNoInventoryItemReturnsUninitialized() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Missing Inventory");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID variantId = createVariant(ownerSubject, organizationId, storeId);

        mockMvc.perform(get(inventoryUrl(organizationId, storeId, variantId)).with(jwtSubject(ownerSubject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variantId").value(variantId.toString()))
                .andExpect(jsonPath("$.initialized").value(false))
                .andExpect(jsonPath("$.onHandQuantity").value(0));

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_item WHERE product_variant_id = ?", Long.class, variantId);
        assertThat(count).isZero();
    }

    @Test
    void nonexistentVariantReturnsNotFound() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Missing Variant");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        mockMvc.perform(get(inventoryUrl(organizationId, storeId, UUID.randomUUID())).with(jwtSubject(ownerSubject)))
                .andExpect(status().isNotFound());
    }

    @Test
    void variantFromAnotherStoreReturnsNotFoundAfterAuthorizationToTheRequestedStore() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Foreign Variant");
        UUID storeAId = createStore(ownerSubject, organizationId, "store-a-" + UUID.randomUUID(), "EGP");
        UUID storeBId = createStore(ownerSubject, organizationId, "store-b-" + UUID.randomUUID(), "EGP");
        UUID variantInStoreA = createVariant(ownerSubject, organizationId, storeAId);

        mockMvc.perform(get(inventoryUrl(organizationId, storeBId, variantInStoreA)).with(jwtSubject(ownerSubject)))
                .andExpect(status().isNotFound());
    }
}
