package io.github.abdelrahmankhalil.aicommerce.inventory;

import tools.jackson.databind.ObjectMapper;
import io.github.abdelrahmankhalil.aicommerce.support.AbstractIntegrationTest;
import io.github.abdelrahmankhalil.aicommerce.support.TenancyFixtures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared HTTP-level fixtures for Inventory integration tests: provisioning a
 * UserAccount, creating an Organization/Store/Product/ProductVariant through the real
 * tenancy/catalog APIs, and granting Store-scoped access - every test exercises the
 * real write paths rather than seeding rows directly, except where a raw-SQL fixture
 * is unavoidable (mirrors {@code catalog.AbstractCatalogIntegrationTest}; duplicated
 * locally rather than depending on catalog's test-only helper, which lives in a
 * different, inaccessible package).
 */
abstract class AbstractInventoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected DataSource dataSource;

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

    protected UUID createStore(String ownerSubject, UUID organizationId, String slug, String currencyCode)
            throws Exception {
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

    /** Creates a Product with a single Variant of the given status, returning the Variant id. */
    protected UUID createVariant(String subject, UUID organizationId, UUID storeId, String variantStatus)
            throws Exception {
        Map<String, Object> body = Map.of(
                "title", "Inventory Product " + UUID.randomUUID(),
                "slug", "inventory-product-" + UUID.randomUUID(),
                "status", "ACTIVE",
                "categoryIds", List.of(),
                "variants", List.of(Map.of(
                        "sku", "SKU-" + UUID.randomUUID(),
                        "status", variantStatus,
                        "price", new BigDecimal("9.99").toString(),
                        "attributes", Map.of())),
                "images", List.of());

        String json = mockMvc.perform(post("/api/organizations/{orgId}/stores/{storeId}/products", organizationId, storeId)
                        .with(jwtSubject(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID productId = UUID.fromString(objectMapper.readTree(json).get("productId").asText());

        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id FROM product_variant WHERE product_id = ?", String.class, productId));
    }

    protected UUID createVariant(String subject, UUID organizationId, UUID storeId) throws Exception {
        return createVariant(subject, organizationId, storeId, "ACTIVE");
    }

    protected String inventoryUrl(UUID organizationId, UUID storeId) {
        return "/api/organizations/" + organizationId + "/stores/" + storeId + "/inventory";
    }

    protected String inventoryUrl(UUID organizationId, UUID storeId, UUID variantId) {
        return inventoryUrl(organizationId, storeId) + "/" + variantId;
    }
}
