package io.github.abdelrahmankhalil.aicommerce.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Variant attribute rules (ADR 006): a flat String -> String map, never null,
 * duplicate combinations within the same Product rejected regardless of JSON key
 * order, the same combination allowed on different Products.
 */
class VariantAttributesIT extends AbstractCatalogIntegrationTest {

    @Test
    void duplicateAttributeCombinationWithinSameProductIsRejectedViaAddVariantEndpoint() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Attr Dup");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID productId = createSimpleProduct(ownerSubject, organizationId, storeId, "Shirt", "shirt");
        // The initial Variant from createSimpleProduct has attributes = {}.

        mockMvc.perform(post(productsUrl(organizationId, storeId) + "/{productId}/variants", productId)
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                variant("SHIRT-2", "ACTIVE", new java.math.BigDecimal("12.00"), Map.of()))))
                .andExpect(status().isConflict());
    }

    @Test
    void jsonKeyOrderingCannotBypassDuplicatePrevention() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Attr Order");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        String createBody = """
                {
                  "title": "Ordered Shirt",
                  "slug": "ordered-shirt",
                  "status": "ACTIVE",
                  "categoryIds": [],
                  "variants": [
                    {"sku": "ORD-1", "status": "ACTIVE", "price": "10.00", "attributes": {"color": "red", "size": "M"}}
                  ],
                  "images": []
                }
                """;
        String json = mockMvc.perform(post(productsUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID productId = UUID.fromString(objectMapper.readTree(json).get("productId").asText());

        // Same attribute combination, keys reordered: still a duplicate.
        String reorderedVariant = """
                {"sku": "ORD-2", "status": "ACTIVE", "price": "10.00", "attributes": {"size": "M", "color": "red"}}
                """;
        mockMvc.perform(post(productsUrl(organizationId, storeId) + "/{productId}/variants", productId)
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reorderedVariant))
                .andExpect(status().isConflict());
    }

    @Test
    void sameAttributeCombinationAllowedOnDifferentProducts() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Attr Cross Product");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        Map<String, Object> firstBody = new java.util.HashMap<>(simpleProductBody("Shirt A", "shirt-a"));
        firstBody.put("variants", java.util.List.of(
                variant("SHIRT-A-RED", "ACTIVE", new java.math.BigDecimal("10.00"), Map.of("color", "red"))));
        mockMvc.perform(post(productsUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstBody)))
                .andExpect(status().isCreated());

        Map<String, Object> secondBody = new java.util.HashMap<>(simpleProductBody("Shirt B", "shirt-b"));
        secondBody.put("variants", java.util.List.of(
                variant("SHIRT-B-RED", "ACTIVE", new java.math.BigDecimal("10.00"), Map.of("color", "red"))));
        mockMvc.perform(post(productsUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondBody)))
                .andExpect(status().isCreated());
    }

    @Test
    void nestedAttributeValueIsRejected() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Attr Nested");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        String createBody = """
                {
                  "title": "Nested Shirt",
                  "slug": "nested-shirt",
                  "status": "ACTIVE",
                  "categoryIds": [],
                  "variants": [
                    {"sku": "NEST-1", "status": "ACTIVE", "price": "10.00", "attributes": {"color": {"nested": "red"}}}
                  ],
                  "images": []
                }
                """;
        mockMvc.perform(post(productsUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isBadRequest());
    }
}
