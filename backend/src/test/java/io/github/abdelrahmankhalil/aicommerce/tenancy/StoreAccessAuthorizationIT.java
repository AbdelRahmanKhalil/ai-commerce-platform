package io.github.abdelrahmankhalil.aicommerce.tenancy;

import tools.jackson.databind.ObjectMapper;
import io.github.abdelrahmankhalil.aicommerce.support.AbstractIntegrationTest;
import io.github.abdelrahmankhalil.aicommerce.support.TenancyFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The four mandatory tenant-isolation cases from CLAUDE.md/ADR 004, exercised through
 * the real HTTP surface: authorized access works; a user from another Organization
 * cannot read or mutate; a MEMBER without a matching StoreAccess grant cannot access
 * the Store, and can once granted.
 */
class StoreAccessAuthorizationIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ownerCanAccessTheirOwnStore() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Owner Co");
        UUID storeId = createStore(ownerSubject, organizationId, "owner-store-" + UUID.randomUUID(), "EGP");

        mockMvc.perform(get("/api/organizations/{orgId}/stores/{storeId}", organizationId, storeId)
                        .with(jwtSubject(ownerSubject)))
                .andExpect(status().isOk());
    }

    @Test
    void outsiderCannotReadAnotherOrganizationsStore() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org A");
        UUID storeId = createStore(ownerSubject, organizationId, "org-a-store-" + UUID.randomUUID(), "EGP");

        String outsiderSubject = provisionedSubject();

        mockMvc.perform(get("/api/organizations/{orgId}/stores/{storeId}", organizationId, storeId)
                        .with(jwtSubject(outsiderSubject)))
                .andExpect(status().isForbidden());
    }

    @Test
    void outsiderCannotCreateAStoreInAnotherOrganization() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org B");

        String outsiderSubject = provisionedSubject();

        mockMvc.perform(post("/api/organizations/{orgId}/stores", organizationId)
                        .with(jwtSubject(outsiderSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Intruder Store",
                                "slug", "intruder-store-" + UUID.randomUUID(),
                                "currencyCode", "EGP"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberWithoutStoreAccessIsDeniedAndGrantedMemberIsAllowed() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org C");
        UUID storeId = createStore(ownerSubject, organizationId, "org-c-store-" + UUID.randomUUID(), "EGP");

        String memberSubject = provisionedSubject();
        UUID memberUserAccountId = userAccountId(memberSubject);
        TenancyFixtures.addMembership(jdbcTemplate, memberUserAccountId, organizationId, "MEMBER");

        mockMvc.perform(get("/api/organizations/{orgId}/stores/{storeId}", organizationId, storeId)
                        .with(jwtSubject(memberSubject)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/organizations/{orgId}/stores/{storeId}/store-access", organizationId, storeId)
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userAccountId", memberUserAccountId.toString(),
                                "role", "CATALOG_EDITOR"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/organizations/{orgId}/stores/{storeId}", organizationId, storeId)
                        .with(jwtSubject(memberSubject)))
                .andExpect(status().isOk());
    }

    private String provisionedSubject() throws Exception {
        String subject = "subject-" + UUID.randomUUID();
        mockMvc.perform(post("/api/me").with(jwtSubject(subject))).andExpect(status().isOk());
        return subject;
    }

    private UUID userAccountId(String subject) {
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id FROM user_account WHERE keycloak_subject = ?", String.class, subject));
    }

    private UUID createOrganization(String ownerSubject, String name) throws Exception {
        String json = mockMvc.perform(post("/api/organizations")
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("organizationId").asText());
    }

    private UUID createStore(String ownerSubject, UUID organizationId, String slug, String currencyCode) throws Exception {
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
}
