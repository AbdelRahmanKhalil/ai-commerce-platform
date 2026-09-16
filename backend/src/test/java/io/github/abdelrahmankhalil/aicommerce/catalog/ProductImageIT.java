package io.github.abdelrahmankhalil.aicommerce.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ProductImage ordering (ADR 006): client array order determines stored position,
 * Product update fully replaces the image set, and duplicate positions are rejected
 * at the database layer.
 */
class ProductImageIT extends AbstractCatalogIntegrationTest {

    @Test
    void clientImageOrderBecomesStoredPositionsZeroToNMinusOne() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Image Order");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        Map<String, Object> body = new HashMap<>(simpleProductBody("Gallery", "gallery"));
        body.put("images", List.of(
                Map.of("url", "https://example.com/a.png", "altText", "A"),
                Map.of("url", "https://example.com/b.png", "altText", "B"),
                Map.of("url", "https://example.com/c.png", "altText", "C")));

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
                .andExpect(jsonPath("$.images[0].url").value("https://example.com/a.png"))
                .andExpect(jsonPath("$.images[0].position").value(0))
                .andExpect(jsonPath("$.images[1].url").value("https://example.com/b.png"))
                .andExpect(jsonPath("$.images[1].position").value(1))
                .andExpect(jsonPath("$.images[2].url").value("https://example.com/c.png"))
                .andExpect(jsonPath("$.images[2].position").value(2));
    }

    @Test
    void productUpdateFullyReplacesImagesAndEmptyListRemovesAll() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Image Replace");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        Map<String, Object> createBody = new HashMap<>(simpleProductBody("Gallery Replace", "gallery-replace"));
        createBody.put("images", List.of(Map.of("url", "https://example.com/old.png", "altText", "Old")));
        String json = mockMvc.perform(post(productsUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID productId = UUID.fromString(objectMapper.readTree(json).get("productId").asText());

        Map<String, Object> updateBody = Map.of(
                "title", "Gallery Replace",
                "slug", "gallery-replace",
                "status", "ACTIVE",
                "categoryIds", List.of(),
                "images", List.of(Map.of("url", "https://example.com/new.png", "altText", "New")));
        mockMvc.perform(put(productsUrl(organizationId, storeId) + "/{productId}", productId)
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk());

        mockMvc.perform(get(productsUrl(organizationId, storeId) + "/{productId}", productId)
                        .with(jwtSubject(ownerSubject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.images[0].url").value("https://example.com/new.png"))
                .andExpect(jsonPath("$.images[0].position").value(0));

        Long oldImageCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_image WHERE product_id = ? AND url = ?", Long.class, productId,
                "https://example.com/old.png");
        assertThat(oldImageCount).isZero();

        // Empty images list: removes all images.
        Map<String, Object> emptyImagesBody = Map.of(
                "title", "Gallery Replace",
                "slug", "gallery-replace",
                "status", "ACTIVE",
                "categoryIds", List.of(),
                "images", List.of());
        mockMvc.perform(put(productsUrl(organizationId, storeId) + "/{productId}", productId)
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyImagesBody)))
                .andExpect(status().isOk());

        Long remainingImages = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_image WHERE product_id = ?", Long.class, productId);
        assertThat(remainingImages).isZero();
    }

    @Test
    void duplicatePositionCannotExistAtTheDatabaseLayer() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Image Dup Position");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID productId = createSimpleProduct(ownerSubject, organizationId, storeId, "Dup Position", "dup-position");

        UUID organizationIdInner = organizationId;
        assertThatThrownBy(() -> {
            jdbcTemplate.update(
                    "INSERT INTO product_image (id, organization_id, store_id, product_id, url, position, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, 0, ?)",
                    UUID.randomUUID(), organizationIdInner, storeId, productId, "https://example.com/x.png",
                    OffsetDateTime.now());
            jdbcTemplate.update(
                    "INSERT INTO product_image (id, organization_id, store_id, product_id, url, position, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, 0, ?)",
                    UUID.randomUUID(), organizationIdInner, storeId, productId, "https://example.com/y.png",
                    OffsetDateTime.now());
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
