package io.github.abdelrahmankhalil.aicommerce.catalog;

import tools.jackson.databind.ObjectMapper;
import io.github.abdelrahmankhalil.aicommerce.support.AbstractIntegrationTest;
import io.github.abdelrahmankhalil.aicommerce.support.TenancyFixtures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared HTTP-level fixtures for catalog integration tests: provisioning a
 * UserAccount, creating an Organization/Store through the real tenancy API, granting
 * Store-scoped access, and creating a minimal valid Product/Category through the
 * real catalog API - so every test exercises the actual write paths rather than
 * seeding rows directly, except where a raw-SQL fixture is unavoidable (e.g. adding a
 * plain MEMBER Membership, exactly as {@code TenancyFixtures} already does for
 * tenancy's own tests).
 */
abstract class AbstractCatalogIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected String provisionedSubject() throws Exception {
        String subject = "subject-" + UUID.randomUUID();
        mockMvc.perform(post("/api/me").with(jwtSubject(subject))).andExpect(status().isOk());
        return subject;
    }

    protected UUID userAccountId(String subject) {
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id FROM user_account WHERE keycloak_subject = ?", String.class, subject));
    }

    protected UUID addMember(String subject, UUID organizationId) {
        UUID userAccountId = userAccountId(subject);
        TenancyFixtures.addMembership(jdbcTemplate, userAccountId, organizationId, "MEMBER");
        return userAccountId;
    }

    protected UUID createOrganization(String ownerSubject, String name) throws Exception {
        String json = mockMvc.perform(post("/api/organizations")
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("organizationId").asText());
    }

    protected UUID createStore(String ownerSubject, UUID organizationId, String slug, String currencyCode) throws Exception {
        String json = mockMvc.perform(post("/api/organizations/{orgId}/stores", organizationId)
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Test Store",
                                "slug", slug,
                                "currencyCode", currencyCode))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("storeId").asText());
    }

    protected void grantStoreAccess(String ownerSubject, UUID organizationId, UUID storeId, UUID targetUserAccountId,
                                     String role) throws Exception {
        mockMvc.perform(post("/api/organizations/{orgId}/stores/{storeId}/store-access", organizationId, storeId)
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userAccountId", targetUserAccountId.toString(),
                                "role", role))))
                .andExpect(status().isCreated());
    }

    protected String categoriesUrl(UUID organizationId, UUID storeId) {
        return "/api/organizations/" + organizationId + "/stores/" + storeId + "/categories";
    }

    protected String productsUrl(UUID organizationId, UUID storeId) {
        return "/api/organizations/" + organizationId + "/stores/" + storeId + "/products";
    }

    protected UUID createCategory(String subject, UUID organizationId, UUID storeId, String name, String slug)
            throws Exception {
        String json = mockMvc.perform(post(categoriesUrl(organizationId, storeId))
                        .with(jwtSubject(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name, "slug", slug))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("categoryId").asText());
    }

    /** A minimal, valid CreateProductRequest body: one Variant, attributes = {}. */
    protected Map<String, Object> simpleProductBody(String title, String slug) {
        return Map.of(
                "title", title,
                "slug", slug,
                "status", "ACTIVE",
                "categoryIds", List.of(),
                "variants", List.of(Map.of(
                        "sku", "SKU-" + UUID.randomUUID(),
                        "status", "ACTIVE",
                        "price", "19.99",
                        "attributes", Map.of())),
                "images", List.of());
    }

    protected UUID createSimpleProduct(String subject, UUID organizationId, UUID storeId, String title, String slug)
            throws Exception {
        String json = mockMvc.perform(post(productsUrl(organizationId, storeId))
                        .with(jwtSubject(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(simpleProductBody(title, slug))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("productId").asText());
    }

    protected Map<String, Object> variant(String sku, String status, BigDecimal price, Map<String, String> attributes) {
        return Map.of("sku", sku, "status", status, "price", price.toString(), "attributes", attributes);
    }
}
