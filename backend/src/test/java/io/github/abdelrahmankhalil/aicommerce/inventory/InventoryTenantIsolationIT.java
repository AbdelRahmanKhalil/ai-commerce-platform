package io.github.abdelrahmankhalil.aicommerce.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The TENANT ISOLATION cases required by the task brief/CLAUDE.md/ADR 004: a user
 * from a different Organization can neither read nor mutate another Organization's
 * Inventory, and a MEMBER whose StoreAccess is scoped to one Store cannot read or
 * mutate another Store's Inventory in the same Organization.
 */
class InventoryTenantIsolationIT extends AbstractInventoryIntegrationTest {

    @Test
    void ownerOfAnotherOrganizationCannotReadAForeignStoresInventory() throws Exception {
        String ownerASubject = provisionedSubject();
        UUID organizationAId = createOrganization(ownerASubject, "Org Inv A");
        UUID storeAId = createStore(ownerASubject, organizationAId, "store-a-" + UUID.randomUUID(), "EGP");
        UUID variantId = createVariant(ownerASubject, organizationAId, storeAId);

        String ownerBSubject = provisionedSubject();
        UUID organizationBId = createOrganization(ownerBSubject, "Org Inv B");

        mockMvc.perform(get(inventoryUrl(organizationBId, storeAId, variantId)).with(jwtSubject(ownerBSubject)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(inventoryUrl(organizationBId, storeAId)).with(jwtSubject(ownerBSubject)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerOfAnotherOrganizationCannotMutateAForeignStoresInventory() throws Exception {
        String ownerASubject = provisionedSubject();
        UUID organizationAId = createOrganization(ownerASubject, "Org Inv A2");
        UUID storeAId = createStore(ownerASubject, organizationAId, "store-a2-" + UUID.randomUUID(), "EGP");
        UUID variantId = createVariant(ownerASubject, organizationAId, storeAId);

        String ownerBSubject = provisionedSubject();
        UUID organizationBId = createOrganization(ownerBSubject, "Org Inv B2");

        mockMvc.perform(put(inventoryUrl(organizationBId, storeAId, variantId))
                        .with(jwtSubject(ownerBSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("onHandQuantity", 10))))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberWithStoreAccessToOneStoreCannotReadAnotherStoreInSameOrganization() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Inv C");
        UUID storeAId = createStore(ownerSubject, organizationId, "store-c-a-" + UUID.randomUUID(), "EGP");
        UUID storeBId = createStore(ownerSubject, organizationId, "store-c-b-" + UUID.randomUUID(), "EGP");
        UUID variantInStoreB = createVariant(ownerSubject, organizationId, storeBId);

        String memberSubject = provisionedSubject();
        UUID memberUserAccountId = addMember(memberSubject, organizationId);
        grantStoreAccess(ownerSubject, organizationId, storeAId, memberUserAccountId, "MANAGER");

        mockMvc.perform(get(inventoryUrl(organizationId, storeBId, variantInStoreB)).with(jwtSubject(memberSubject)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(inventoryUrl(organizationId, storeBId)).with(jwtSubject(memberSubject)))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberWithStoreAccessToOneStoreCannotMutateAnotherStoreInSameOrganization() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Inv D");
        UUID storeAId = createStore(ownerSubject, organizationId, "store-d-a-" + UUID.randomUUID(), "EGP");
        UUID storeBId = createStore(ownerSubject, organizationId, "store-d-b-" + UUID.randomUUID(), "EGP");
        UUID variantInStoreB = createVariant(ownerSubject, organizationId, storeBId);

        String memberSubject = provisionedSubject();
        UUID memberUserAccountId = addMember(memberSubject, organizationId);
        grantStoreAccess(ownerSubject, organizationId, storeAId, memberUserAccountId, "MANAGER");

        mockMvc.perform(put(inventoryUrl(organizationId, storeBId, variantInStoreB))
                        .with(jwtSubject(memberSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("onHandQuantity", 10))))
                .andExpect(status().isForbidden());
    }
}
