package io.github.abdelrahmankhalil.aicommerce.inventory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The AUTHORIZATION cases required by the task brief (ADR 007): OWNER/ADMIN
 * (Organization-wide) and MANAGER/ORDER_MANAGER (Store-scoped) may read and write
 * Inventory; CATALOG_EDITOR and SUPPORT_AGENT may read but never write. No {@code
 * INVENTORY_MANAGER} role exists.
 */
class InventoryAuthorizationIT extends AbstractInventoryIntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN"})
    void organizationWideRolesCanReadAndWriteInventory(String organizationRole) throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Inv " + organizationRole);
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID variantId = createVariant(ownerSubject, organizationId, storeId);

        String staffSubject = provisionedSubject();
        UUID staffUserAccountId = userAccountId(staffSubject);
        io.github.abdelrahmankhalil.aicommerce.support.TenancyFixtures.addMembership(
                jdbcTemplate, staffUserAccountId, organizationId, organizationRole);

        assertCanReadAndWrite(staffSubject, organizationId, storeId, variantId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "ORDER_MANAGER"})
    void storeScopedWriteRolesCanReadAndWriteInventory(String storeRole) throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Inv " + storeRole);
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID variantId = createVariant(ownerSubject, organizationId, storeId);

        String memberSubject = provisionedSubject();
        UUID memberUserAccountId = addMember(memberSubject, organizationId);
        grantStoreAccess(ownerSubject, organizationId, storeId, memberUserAccountId, storeRole);

        assertCanReadAndWrite(memberSubject, organizationId, storeId, variantId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CATALOG_EDITOR", "SUPPORT_AGENT"})
    void readOnlyStoreRolesCanReadButNotWriteInventory(String storeRole) throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Inv RO " + storeRole);
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID variantId = createVariant(ownerSubject, organizationId, storeId);

        String memberSubject = provisionedSubject();
        UUID memberUserAccountId = addMember(memberSubject, organizationId);
        grantStoreAccess(ownerSubject, organizationId, storeId, memberUserAccountId, storeRole);

        mockMvc.perform(get(inventoryUrl(organizationId, storeId, variantId)).with(jwtSubject(memberSubject)))
                .andExpect(status().isOk());
        mockMvc.perform(get(inventoryUrl(organizationId, storeId)).with(jwtSubject(memberSubject)))
                .andExpect(status().isOk());

        mockMvc.perform(put(inventoryUrl(organizationId, storeId, variantId))
                        .with(jwtSubject(memberSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("onHandQuantity", 10))))
                .andExpect(status().isForbidden());
    }

    private void assertCanReadAndWrite(String subject, UUID organizationId, UUID storeId, UUID variantId)
            throws Exception {
        mockMvc.perform(get(inventoryUrl(organizationId, storeId, variantId)).with(jwtSubject(subject)))
                .andExpect(status().isOk());
        mockMvc.perform(get(inventoryUrl(organizationId, storeId)).with(jwtSubject(subject)))
                .andExpect(status().isOk());
        mockMvc.perform(put(inventoryUrl(organizationId, storeId, variantId))
                        .with(jwtSubject(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("onHandQuantity", 10))))
                .andExpect(status().isCreated());
    }
}
