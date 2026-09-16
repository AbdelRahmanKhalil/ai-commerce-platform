package io.github.abdelrahmankhalil.aicommerce.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Product creation atomicity (ADR 006): every Product is created together with at
 * least one Variant, its Category associations, and any Images, all in one
 * transaction - any failure rolls back the entire Product.
 */
class ProductCreationIT extends AbstractCatalogIntegrationTest {

    @Test
    void zeroInitialVariantsIsRejectedAndNoProductIsPersisted() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Zero Variant");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        Map<String, Object> body = Map.of(
                "title", "No Variant Product",
                "slug", "no-variant-product",
                "status", "ACTIVE",
                "categoryIds", List.of(),
                "variants", List.of(),
                "images", List.of());

        mockMvc.perform(post(productsUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        Long productCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE store_id = ?", Long.class, storeId);
        assertThat(productCount).isZero();
    }

    @Test
    void simpleProductWithOneVariantAndEmptyAttributesSucceeds() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Simple Product");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        UUID productId = createSimpleProduct(ownerSubject, organizationId, storeId, "Plain Shirt", "plain-shirt");

        mockMvc.perform(get(productsUrl(organizationId, storeId) + "/{productId}", productId)
                        .with(jwtSubject(ownerSubject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.variants[0].attributes").isEmpty());
    }

    @Test
    void productWithMultipleInitialVariantsSucceeds() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Multi Variant");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        Map<String, Object> body = new HashMap<>(simpleProductBody("T-Shirt", "t-shirt"));
        body.put("variants", List.of(
                variant("TS-RED", "ACTIVE", new java.math.BigDecimal("10.00"), Map.of("color", "red")),
                variant("TS-BLUE", "ACTIVE", new java.math.BigDecimal("10.00"), Map.of("color", "blue"))));

        String json = mockMvc.perform(post(productsUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID productId = UUID.fromString(objectMapper.readTree(json).get("productId").asText());

        mockMvc.perform(get(productsUrl(organizationId, storeId) + "/{productId}", productId)
                        .with(jwtSubject(ownerSubject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void duplicateAttributeCombinationAmongInitialVariantsRollsBackTheWholeProduct() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Rollback");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        Map<String, Object> body = new HashMap<>(simpleProductBody("Broken Product", "broken-product"));
        body.put("variants", List.of(
                variant("BP-1", "ACTIVE", new java.math.BigDecimal("10.00"), Map.of("color", "red")),
                variant("BP-2", "ACTIVE", new java.math.BigDecimal("10.00"), Map.of("color", "red"))));

        mockMvc.perform(post(productsUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());

        Long productCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE store_id = ? AND slug = ?", Long.class, storeId, "broken-product");
        assertThat(productCount).isZero();
        Long variantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_variant WHERE store_id = ?", Long.class, storeId);
        assertThat(variantCount).isZero();
    }

    @Test
    void duplicateSkuAmongInitialVariantsRollsBackTheWholeProduct() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Rollback Sku");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        Map<String, Object> body = new HashMap<>(simpleProductBody("Broken Product Sku", "broken-product-sku"));
        body.put("variants", List.of(
                variant("DUPE-SKU", "ACTIVE", new java.math.BigDecimal("10.00"), Map.of("color", "red")),
                variant("dupe-sku", "ACTIVE", new java.math.BigDecimal("10.00"), Map.of("color", "blue"))));

        mockMvc.perform(post(productsUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());

        Long productCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE store_id = ? AND slug = ?", Long.class, storeId,
                "broken-product-sku");
        assertThat(productCount).isZero();
    }
}
