package io.github.abdelrahmankhalil.aicommerce.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Product slug and Variant SKU rules (ADR 006). */
class ProductSlugAndSkuIT extends AbstractCatalogIntegrationTest {

    @Test
    void sameProductSlugAllowedAcrossStoresButRejectedWithinTheSameStore() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Prod Slug");
        UUID storeAId = createStore(ownerSubject, organizationId, "store-a-" + UUID.randomUUID(), "EGP");
        UUID storeBId = createStore(ownerSubject, organizationId, "store-b-" + UUID.randomUUID(), "EGP");

        createSimpleProduct(ownerSubject, organizationId, storeAId, "Shared Slug", "shared-slug");
        // Same slug, different Store: allowed.
        createSimpleProduct(ownerSubject, organizationId, storeBId, "Shared Slug", "shared-slug");

        // Same slug, same Store: rejected as a conflict.
        mockMvc.perform(post(productsUrl(organizationId, storeAId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(simpleProductBody("Duplicate", "shared-slug"))))
                .andExpect(status().isConflict());
    }

    @Test
    void skuIsTrimmedBeforePersistenceAndCasingIsPreserved() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Sku Trim");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        Map<String, Object> body = new HashMap<>(simpleProductBody("Trim Product", "trim-product"));
        body.put("variants", List.of(variant("  Abc-123  ", "ACTIVE", new BigDecimal("5.00"), Map.of())));

        mockMvc.perform(post(productsUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        String persistedSku = jdbcTemplate.queryForObject(
                "SELECT sku FROM product_variant WHERE store_id = ?", String.class, storeId);
        assertThat(persistedSku).isEqualTo("Abc-123");
    }

    @Test
    void skusConflictCaseInsensitivelyWithinTheSameStore() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Sku Case");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        Map<String, Object> firstBody = new HashMap<>(simpleProductBody("First", "first"));
        firstBody.put("variants", List.of(variant("ABC-123", "ACTIVE", new BigDecimal("5.00"), Map.of())));
        mockMvc.perform(post(productsUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstBody)))
                .andExpect(status().isCreated());

        Map<String, Object> secondBody = new HashMap<>(simpleProductBody("Second", "second"));
        secondBody.put("variants", List.of(variant("abc-123", "ACTIVE", new BigDecimal("5.00"), Map.of())));
        mockMvc.perform(post(productsUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondBody)))
                .andExpect(status().isConflict());
    }

    @Test
    void sameSkuAllowedAcrossDifferentStores() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Sku Cross Store");
        UUID storeAId = createStore(ownerSubject, organizationId, "store-a-" + UUID.randomUUID(), "EGP");
        UUID storeBId = createStore(ownerSubject, organizationId, "store-b-" + UUID.randomUUID(), "EGP");

        Map<String, Object> bodyA = new HashMap<>(simpleProductBody("Product A", "product-a"));
        bodyA.put("variants", List.of(variant("SHARED-SKU", "ACTIVE", new BigDecimal("5.00"), Map.of())));
        mockMvc.perform(post(productsUrl(organizationId, storeAId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bodyA)))
                .andExpect(status().isCreated());

        Map<String, Object> bodyB = new HashMap<>(simpleProductBody("Product B", "product-b"));
        bodyB.put("variants", List.of(variant("SHARED-SKU", "ACTIVE", new BigDecimal("5.00"), Map.of())));
        mockMvc.perform(post(productsUrl(organizationId, storeBId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bodyB)))
                .andExpect(status().isCreated());
    }

    @Test
    void blankAfterTrimSkuIsRejected() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Sku Blank");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        Map<String, Object> body = new HashMap<>(simpleProductBody("Blank Sku", "blank-sku"));
        body.put("variants", List.of(variant("   ", "ACTIVE", new BigDecimal("5.00"), Map.of())));

        mockMvc.perform(post(productsUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}
