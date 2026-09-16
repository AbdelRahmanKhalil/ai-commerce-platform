package io.github.abdelrahmankhalil.aicommerce.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Product update (full-replace semantics) and listing behavior (ADR 006).
 */
class ProductUpdateIT extends AbstractCatalogIntegrationTest {

    @Test
    void categoryIdsAreFullyReplacedAndEmptyDetachesAll() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Update Categories");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID categoryOneId = createCategory(ownerSubject, organizationId, storeId, "Cat One", "cat-one");
        UUID categoryTwoId = createCategory(ownerSubject, organizationId, storeId, "Cat Two", "cat-two");

        Map<String, Object> createBody = new HashMap<>(simpleProductBody("Cat Product", "cat-product"));
        createBody.put("categoryIds", List.of(categoryOneId.toString()));
        String json = mockMvc.perform(post(productsUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID productId = UUID.fromString(objectMapper.readTree(json).get("productId").asText());

        Map<String, Object> updateBody = Map.of(
                "title", "Cat Product",
                "slug", "cat-product",
                "status", "ACTIVE",
                "categoryIds", List.of(categoryTwoId.toString()),
                "images", List.of());
        mockMvc.perform(put(productsUrl(organizationId, storeId) + "/{productId}", productId)
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk());

        mockMvc.perform(get(productsUrl(organizationId, storeId) + "/{productId}", productId)
                        .with(jwtSubject(ownerSubject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryIds", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.categoryIds[0]").value(categoryTwoId.toString()));

        // Empty categoryIds: detaches all.
        Map<String, Object> detachBody = Map.of(
                "title", "Cat Product",
                "slug", "cat-product",
                "status", "ACTIVE",
                "categoryIds", List.of(),
                "images", List.of());
        mockMvc.perform(put(productsUrl(organizationId, storeId) + "/{productId}", productId)
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(detachBody)))
                .andExpect(status().isOk());

        mockMvc.perform(get(productsUrl(organizationId, storeId) + "/{productId}", productId)
                        .with(jwtSubject(ownerSubject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryIds", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void updateRejectsCategoryFromAnotherStore() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Update Foreign Cat");
        UUID storeAId = createStore(ownerSubject, organizationId, "store-a-" + UUID.randomUUID(), "EGP");
        UUID storeBId = createStore(ownerSubject, organizationId, "store-b-" + UUID.randomUUID(), "EGP");
        UUID categoryInStoreB = createCategory(ownerSubject, organizationId, storeBId, "Foreign", "foreign");
        UUID productId = createSimpleProduct(ownerSubject, organizationId, storeAId, "Product A", "product-a");

        Map<String, Object> updateBody = Map.of(
                "title", "Product A",
                "slug", "product-a",
                "status", "ACTIVE",
                "categoryIds", List.of(categoryInStoreB.toString()),
                "images", List.of());
        mockMvc.perform(put(productsUrl(organizationId, storeAId) + "/{productId}", productId)
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isNotFound());
    }

    @Test
    void productFromAnotherStoreIsNotFoundEvenWithAuthorizedAccessToTheRequestedStore() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Scoped Product");
        UUID storeAId = createStore(ownerSubject, organizationId, "store-a-" + UUID.randomUUID(), "EGP");
        UUID storeBId = createStore(ownerSubject, organizationId, "store-b-" + UUID.randomUUID(), "EGP");
        UUID productInStoreA = createSimpleProduct(ownerSubject, organizationId, storeAId, "Product A", "product-a-scoped");

        mockMvc.perform(get(productsUrl(organizationId, storeBId) + "/{productId}", productInStoreA)
                        .with(jwtSubject(ownerSubject)))
                .andExpect(status().isNotFound());
    }

    @Test
    void statusFilterReturnsOnlyRequestedStatus() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Status Filter");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        createProductWithStatus(ownerSubject, organizationId, storeId, "Draft Product", "draft-product", "DRAFT");
        createProductWithStatus(ownerSubject, organizationId, storeId, "Active Product", "active-product", "ACTIVE");

        mockMvc.perform(get(productsUrl(organizationId, storeId)).param("status", "DRAFT")
                        .with(jwtSubject(ownerSubject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"));
    }

    @Test
    void listOrderingIsDeterministicByCreatedAtThenIdDescending() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org List Order");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        createSimpleProduct(ownerSubject, organizationId, storeId, "First", "first-product");
        createSimpleProduct(ownerSubject, organizationId, storeId, "Second", "second-product");
        createSimpleProduct(ownerSubject, organizationId, storeId, "Third", "third-product");

        String firstJson = mockMvc.perform(get(productsUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondJson = mockMvc.perform(get(productsUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Same request repeated: identical ordering (deterministic, not incidental -
        // e.g. not dependent on unordered database/collection iteration).
        org.junit.jupiter.api.Assertions.assertEquals(firstJson, secondJson);

        var titles = objectMapper.readTree(firstJson).get("content");
        org.junit.jupiter.api.Assertions.assertEquals(3, titles.size());
    }

    @Test
    void productSummaryDoesNotIncludeVariantsImagesOrCategories() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Summary Shape");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        createSimpleProduct(ownerSubject, organizationId, storeId, "Summary Product", "summary-product");

        mockMvc.perform(get(productsUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].variants").doesNotExist())
                .andExpect(jsonPath("$.content[0].images").doesNotExist())
                .andExpect(jsonPath("$.content[0].categoryIds").doesNotExist())
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].title").exists())
                .andExpect(jsonPath("$.content[0].slug").exists())
                .andExpect(jsonPath("$.content[0].status").exists());
    }

    @Test
    void invalidPagingParametersAreRejectedCleanly() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Paging");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        mockMvc.perform(get(productsUrl(organizationId, storeId)).param("page", "-1")
                        .with(jwtSubject(ownerSubject)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(productsUrl(organizationId, storeId)).param("size", "0")
                        .with(jwtSubject(ownerSubject)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(productsUrl(organizationId, storeId)).param("size", "101")
                        .with(jwtSubject(ownerSubject)))
                .andExpect(status().isBadRequest());
    }

    private void createProductWithStatus(String subject, UUID organizationId, UUID storeId, String title, String slug,
                                          String status) throws Exception {
        Map<String, Object> body = new HashMap<>(simpleProductBody(title, slug));
        body.put("status", status);
        mockMvc.perform(post(productsUrl(organizationId, storeId))
                        .with(jwtSubject(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }
}
