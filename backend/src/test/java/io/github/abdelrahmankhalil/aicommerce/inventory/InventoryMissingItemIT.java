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
        UUID requestedVariantId = UUID.randomUUID();

        String body = mockMvc.perform(
                        get(inventoryUrl(organizationId, storeId, requestedVariantId)).with(jwtSubject(ownerSubject)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertSanitizedNotFoundBody(body, requestedVariantId);
    }

    @Test
    void variantFromAnotherStoreReturnsNotFoundAfterAuthorizationToTheRequestedStore() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Foreign Variant");
        UUID storeAId = createStore(ownerSubject, organizationId, "store-a-" + UUID.randomUUID(), "EGP");
        UUID storeBId = createStore(ownerSubject, organizationId, "store-b-" + UUID.randomUUID(), "EGP");
        UUID variantInStoreA = createVariant(ownerSubject, organizationId, storeAId);

        String body = mockMvc.perform(
                        get(inventoryUrl(organizationId, storeBId, variantInStoreA)).with(jwtSubject(ownerSubject)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // The foreign Variant's id must never be echoed back - it would otherwise leak
        // that a given id belongs to a Variant that exists somewhere else.
        assertSanitizedNotFoundBody(body, variantInStoreA);
    }

    /**
     * No SQL/constraint/internal persistence detail ever leaks, and - crucially - the
     * server's own {@code detail}/{@code title} message never echoes the requested or
     * foreign Variant id itself (the response's {@code instance} field naturally
     * mirrors the request URI the client itself sent, per RFC 7807/Spring's
     * ProblemDetail handling, which is not a leak of new information - but the server
     * must never additionally confirm/restate that id in its own error message, e.g. by
     * ever using the raw exception message, which would otherwise distinguish a
     * "foreign Variant" response from a "truly nonexistent" one).
     */
    private void assertSanitizedNotFoundBody(String body, UUID requestedOrForeignVariantId) throws Exception {
        assertThat(body).doesNotContainIgnoringCase("sql")
                .doesNotContainIgnoringCase("hibernate")
                .doesNotContainIgnoringCase("jdbc")
                .doesNotContainIgnoringCase("constraint")
                .doesNotContain("io.github.abdelrahmankhalil");

        var problem = objectMapper.readTree(body);
        assertThat(problem.get("detail").asText()).doesNotContain(requestedOrForeignVariantId.toString());
        assertThat(problem.get("title").asText()).doesNotContain(requestedOrForeignVariantId.toString());
    }
}
