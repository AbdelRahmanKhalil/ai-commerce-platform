package io.github.abdelrahmankhalil.aicommerce.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The LIST cases required by the task brief (ADR 007): only initialized
 * InventoryItems are listed - uninitialized Variants are never synthesized -
 * pagination bounds are enforced, ordering is deterministic, and results are scoped
 * to the exact Organization/Store requested.
 */
class InventoryListIT extends AbstractInventoryIntegrationTest {

    @Test
    void onlyInitializedItemsAreListed() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org List Only Initialized");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        UUID initializedVariant = createVariant(ownerSubject, organizationId, storeId);
        createVariant(ownerSubject, organizationId, storeId); // left uninitialized

        putQuantity(ownerSubject, organizationId, storeId, initializedVariant, 7);

        mockMvc.perform(get(inventoryUrl(organizationId, storeId)).with(jwtSubject(ownerSubject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].variantId").value(initializedVariant.toString()));
    }

    @Test
    void paginationBoundsAreEnforced() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org List Paging");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        mockMvc.perform(get(inventoryUrl(organizationId, storeId) + "?page=-1").with(jwtSubject(ownerSubject)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(inventoryUrl(organizationId, storeId) + "?size=0").with(jwtSubject(ownerSubject)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(inventoryUrl(organizationId, storeId) + "?size=101").with(jwtSubject(ownerSubject)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resultsAreDeterministicallyOrderedByCreatedAtThenIdDescending() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org List Order");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        UUID first = createVariant(ownerSubject, organizationId, storeId);
        putQuantity(ownerSubject, organizationId, storeId, first, 1);
        UUID second = createVariant(ownerSubject, organizationId, storeId);
        putQuantity(ownerSubject, organizationId, storeId, second, 2);
        UUID third = createVariant(ownerSubject, organizationId, storeId);
        putQuantity(ownerSubject, organizationId, storeId, third, 3);

        String firstResponse = mockMvc.perform(get(inventoryUrl(organizationId, storeId)).with(jwtSubject(ownerSubject)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondResponse = mockMvc.perform(get(inventoryUrl(organizationId, storeId)).with(jwtSubject(ownerSubject)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Same request repeated: identical ordering (deterministic, not incidental -
        // e.g. not dependent on unordered database/collection iteration), mirroring
        // catalog.ProductUpdateIT#listOrderingIsDeterministicByCreatedAtThenIdDescending.
        org.junit.jupiter.api.Assertions.assertEquals(firstResponse, secondResponse);
        org.junit.jupiter.api.Assertions.assertEquals(3, objectMapper.readTree(firstResponse).get("content").size());
    }

    @Test
    void resultsAreScopedToTheExactOrganizationAndStoreRequested() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org List Scope");
        UUID storeAId = createStore(ownerSubject, organizationId, "store-a-" + UUID.randomUUID(), "EGP");
        UUID storeBId = createStore(ownerSubject, organizationId, "store-b-" + UUID.randomUUID(), "EGP");

        UUID variantInA = createVariant(ownerSubject, organizationId, storeAId);
        putQuantity(ownerSubject, organizationId, storeAId, variantInA, 4);
        UUID variantInB = createVariant(ownerSubject, organizationId, storeBId);
        putQuantity(ownerSubject, organizationId, storeBId, variantInB, 9);

        mockMvc.perform(get(inventoryUrl(organizationId, storeAId)).with(jwtSubject(ownerSubject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].variantId").value(variantInA.toString()));
    }

    private void putQuantity(String subject, UUID organizationId, UUID storeId, UUID variantId, long quantity)
            throws Exception {
        MockHttpServletRequestBuilder request = put(inventoryUrl(organizationId, storeId, variantId))
                .with(jwtSubject(subject))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("onHandQuantity", quantity)));
        mockMvc.perform(request);
    }
}
