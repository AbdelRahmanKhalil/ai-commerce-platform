package io.github.abdelrahmankhalil.aicommerce.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Category API behavior (ADR 006): CRUD, cascade-on-delete, Store-scoped slugs. */
class CategoryIT extends AbstractCatalogIntegrationTest {

    @Test
    void categoryUpdateWorks() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Cat Update");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID categoryId = createCategory(ownerSubject, organizationId, storeId, "New Arrivals", "new-arrivals");

        mockMvc.perform(put(categoriesUrl(organizationId, storeId) + "/{categoryId}", categoryId)
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Renamed", "slug", "renamed"))))
                .andExpect(status().isOk());

        mockMvc.perform(get(categoriesUrl(organizationId, storeId)).with(jwtSubject(ownerSubject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Renamed"))
                .andExpect(jsonPath("$.content[0].slug").value("renamed"));
    }

    @Test
    void updatingUnknownCategoryReturnsNotFound() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Cat Unknown");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        mockMvc.perform(put(categoriesUrl(organizationId, storeId) + "/{categoryId}", UUID.randomUUID())
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Renamed", "slug", "renamed"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void categoryFromAnotherStoreIsNotFoundEvenWithAuthorizedAccessToTheRequestedStore() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Cat Scoped");
        UUID storeAId = createStore(ownerSubject, organizationId, "store-a-" + UUID.randomUUID(), "EGP");
        UUID storeBId = createStore(ownerSubject, organizationId, "store-b-" + UUID.randomUUID(), "EGP");
        UUID categoryInStoreA = createCategory(ownerSubject, organizationId, storeAId, "New Arrivals", "new-arrivals");

        mockMvc.perform(put(categoriesUrl(organizationId, storeBId) + "/{categoryId}", categoryInStoreA)
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Renamed", "slug", "renamed"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void productCanBelongToMultipleCategoriesAndSurvivesCategoryDeletion() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Multi Cat");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID categoryOneId = createCategory(ownerSubject, organizationId, storeId, "New Arrivals", "new-arrivals");
        UUID categoryTwoId = createCategory(ownerSubject, organizationId, storeId, "Shoes", "shoes");

        Map<String, Object> body = new java.util.HashMap<>(simpleProductBody("Sneaker", "sneaker"));
        body.put("categoryIds", List.of(categoryOneId.toString(), categoryTwoId.toString()));

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
                .andExpect(jsonPath("$.categoryIds", org.hamcrest.Matchers.hasSize(2)));

        // Delete one Category: its association is removed, but the Product survives
        // with the remaining Category association intact.
        mockMvc.perform(delete(categoriesUrl(organizationId, storeId) + "/{categoryId}", categoryOneId)
                        .with(jwtSubject(ownerSubject)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(productsUrl(organizationId, storeId) + "/{productId}", productId)
                        .with(jwtSubject(ownerSubject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryIds", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.categoryIds[0]").value(categoryTwoId.toString()));

        long categoryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM category WHERE id = ?", Long.class, categoryOneId);
        assertThat(categoryCount).isZero();

        long productCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE id = ?", Long.class, productId);
        assertThat(productCount).isEqualTo(1);
    }

    @Test
    void sameSlugAllowedAcrossStoresButRejectedWithinTheSameStore() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Cat Slug");
        UUID storeAId = createStore(ownerSubject, organizationId, "store-a-" + UUID.randomUUID(), "EGP");
        UUID storeBId = createStore(ownerSubject, organizationId, "store-b-" + UUID.randomUUID(), "EGP");

        createCategory(ownerSubject, organizationId, storeAId, "New Arrivals", "new-arrivals");
        // Same slug, different Store: allowed.
        createCategory(ownerSubject, organizationId, storeBId, "New Arrivals", "new-arrivals");

        // Same slug, same Store: rejected as a conflict, not a 500.
        mockMvc.perform(post(categoriesUrl(organizationId, storeAId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Duplicate", "slug", "new-arrivals"))))
                .andExpect(status().isConflict());
    }

    @Test
    void categoryNameMustBeNonBlank() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Cat Blank");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        mockMvc.perform(post(categoriesUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "   ", "slug", "blank-name"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidSlugFormatIsRejected() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Cat Bad Slug");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        mockMvc.perform(post(categoriesUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Bad", "slug", "-bad-slug-"))))
                .andExpect(status().isBadRequest());
    }
}
