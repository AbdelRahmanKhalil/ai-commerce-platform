package io.github.abdelrahmankhalil.aicommerce.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ProductVariant status transitions and foreign Product/Variant safety (ADR 006).
 */
class VariantUpdateIT extends AbstractCatalogIntegrationTest {

    @Test
    void variantStatusMovesFromActiveToInactiveAndBack() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Variant Status");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID productId = createSimpleProduct(ownerSubject, organizationId, storeId, "Status Product", "status-product");

        String detailJson = mockMvc.perform(get(productsUrl(organizationId, storeId) + "/{productId}", productId)
                        .with(jwtSubject(ownerSubject)))
                .andReturn().getResponse().getContentAsString();
        UUID variantId = UUID.fromString(objectMapper.readTree(detailJson).get("variants").get(0).get("id").asText());

        mockMvc.perform(put(productsUrl(organizationId, storeId) + "/{productId}/variants/{variantId}", productId,
                        variantId)
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("sku", "STATUS-1", "price", "9.99", "attributes", Map.of(), "status", "INACTIVE"))))
                .andExpect(status().isOk());

        mockMvc.perform(get(productsUrl(organizationId, storeId) + "/{productId}", productId)
                        .with(jwtSubject(ownerSubject)))
                .andExpect(jsonPath("$.variants[0].status").value("INACTIVE"));

        mockMvc.perform(put(productsUrl(organizationId, storeId) + "/{productId}/variants/{variantId}", productId,
                        variantId)
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("sku", "STATUS-1", "price", "9.99", "attributes", Map.of(), "status", "ACTIVE"))))
                .andExpect(status().isOk());

        mockMvc.perform(get(productsUrl(organizationId, storeId) + "/{productId}", productId)
                        .with(jwtSubject(ownerSubject)))
                .andExpect(jsonPath("$.variants[0].status").value("ACTIVE"));
    }

    @Test
    void updatingAVariantThatBelongsToAnotherProductFailsSafely() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Foreign Variant");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID productAId = createSimpleProduct(ownerSubject, organizationId, storeId, "Product A", "product-a-foreign");
        UUID productBId = createSimpleProduct(ownerSubject, organizationId, storeId, "Product B", "product-b-foreign");

        String detailJson = mockMvc.perform(get(productsUrl(organizationId, storeId) + "/{productId}", productAId)
                        .with(jwtSubject(ownerSubject)))
                .andReturn().getResponse().getContentAsString();
        UUID variantOfProductA = UUID.fromString(objectMapper.readTree(detailJson).get("variants").get(0).get("id").asText());

        // variantOfProductA does not belong to productB: must fail safely, not update.
        mockMvc.perform(put(productsUrl(organizationId, storeId) + "/{productId}/variants/{variantId}", productBId,
                        variantOfProductA)
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("sku", "FOREIGN-1", "price", "9.99", "attributes", Map.of(), "status", "INACTIVE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void addingVariantToUnknownProductFailsSafely() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Unknown Product Variant");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        mockMvc.perform(post(productsUrl(organizationId, storeId) + "/{productId}/variants", UUID.randomUUID())
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                variant("NEW-1", "ACTIVE", new BigDecimal("9.99"), Map.of()))))
                .andExpect(status().isNotFound());
    }
}
