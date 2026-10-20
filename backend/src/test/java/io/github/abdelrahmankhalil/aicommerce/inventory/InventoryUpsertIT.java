package io.github.abdelrahmankhalil.aicommerce.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The INITIALIZATION/UPDATE cases required by the task brief (ADR 007): the first PUT
 * creates the InventoryItem (201 + Location), a later PUT replaces the quantity
 * (200), quantity may be set to zero, negative quantity is rejected and never
 * persisted, and an INACTIVE Variant can be initialized and updated just like an
 * ACTIVE one.
 */
class InventoryUpsertIT extends AbstractInventoryIntegrationTest {

    @Test
    void firstPutCreatesTheInventoryItemWithLocationAndGetThenReturnsItInitialized() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Upsert Create");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID variantId = createVariant(ownerSubject, organizationId, storeId);

        mockMvc.perform(put(inventoryUrl(organizationId, storeId, variantId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("onHandQuantity", 20))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWithInventoryPath(organizationId, storeId, variantId)))
                .andExpect(jsonPath("$.initialized").value(true))
                .andExpect(jsonPath("$.onHandQuantity").value(20));

        mockMvc.perform(get(inventoryUrl(organizationId, storeId, variantId)).with(jwtSubject(ownerSubject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialized").value(true))
                .andExpect(jsonPath("$.onHandQuantity").value(20));
    }

    @Test
    void laterPutReplacesRatherThanIncrementsTheQuantity() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Upsert Replace");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID variantId = createVariant(ownerSubject, organizationId, storeId);

        mockMvc.perform(put(inventoryUrl(organizationId, storeId, variantId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("onHandQuantity", 20))))
                .andExpect(status().isCreated());

        mockMvc.perform(put(inventoryUrl(organizationId, storeId, variantId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("onHandQuantity", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHandQuantity").value(5));

        mockMvc.perform(get(inventoryUrl(organizationId, storeId, variantId)).with(jwtSubject(ownerSubject)))
                .andExpect(jsonPath("$.onHandQuantity").value(5));
    }

    @Test
    void quantityMayBeSetToZero() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Upsert Zero");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID variantId = createVariant(ownerSubject, organizationId, storeId);

        mockMvc.perform(put(inventoryUrl(organizationId, storeId, variantId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("onHandQuantity", 0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.onHandQuantity").value(0));
    }

    @Test
    void negativeQuantityIsRejectedAndNeverPersisted() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Upsert Negative");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID variantId = createVariant(ownerSubject, organizationId, storeId);

        String body = mockMvc.perform(put(inventoryUrl(organizationId, storeId, variantId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("onHandQuantity", -1))))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertSanitizedBadRequestBody(body);

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_item WHERE product_variant_id = ?", Long.class, variantId);
        assertThat(count).isZero();
    }

    @Test
    void negativeQuantityIsRejectedOnAnExistingItemToo() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Upsert Negative Existing");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID variantId = createVariant(ownerSubject, organizationId, storeId);

        mockMvc.perform(put(inventoryUrl(organizationId, storeId, variantId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("onHandQuantity", 10))))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(put(inventoryUrl(organizationId, storeId, variantId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("onHandQuantity", -5))))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertSanitizedBadRequestBody(body);

        Long persisted = jdbcTemplate.queryForObject(
                "SELECT on_hand_quantity FROM inventory_item WHERE product_variant_id = ?", Long.class, variantId);
        assertThat(persisted).isEqualTo(10);
    }

    @Test
    void anInactiveVariantCanBeInitializedAndUpdated() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Upsert Inactive");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID variantId = createVariant(ownerSubject, organizationId, storeId, "INACTIVE");

        mockMvc.perform(put(inventoryUrl(organizationId, storeId, variantId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("onHandQuantity", 15))))
                .andExpect(status().isCreated());

        mockMvc.perform(put(inventoryUrl(organizationId, storeId, variantId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("onHandQuantity", 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHandQuantity").value(3));
    }

    @Test
    void variantNotFoundInScopeIsRejectedBeforeAnyInventoryWrite() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Upsert No Variant");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        mockMvc.perform(put(inventoryUrl(organizationId, storeId, UUID.randomUUID()))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("onHandQuantity", 10))))
                .andExpect(status().isNotFound());
    }

    private static org.hamcrest.Matcher<String> endsWithInventoryPath(UUID organizationId, UUID storeId, UUID variantId) {
        return org.hamcrest.Matchers.endsWith(
                "/api/organizations/" + organizationId + "/stores/" + storeId + "/inventory/" + variantId);
    }

    /** A clean ProblemDetail - never a raw SQL/Hibernate/stack-trace leak. */
    private static void assertSanitizedBadRequestBody(String body) {
        assertThat(body).doesNotContainIgnoringCase("sql")
                .doesNotContainIgnoringCase("hibernate")
                .doesNotContainIgnoringCase("jdbc")
                .doesNotContainIgnoringCase("constraint")
                .doesNotContain("io.github.abdelrahmankhalil");
    }
}
