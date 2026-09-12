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

    /**
     * The "outsider" here is a genuine OWNER of their own, separate Organization - not
     * merely an unaffiliated subject with no Membership anywhere. That distinction
     * matters: an unaffiliated subject is rejected by the very first Membership
     * lookup in {@code TenancyAuthorizationService}, without ever reaching the
     * Store/Organization consistency check
     * ({@code storeRepository.findByIdAndOrganizationId}) that guards the realistic
     * attack - a legitimate principal, acting with their own valid Organization
     * context, supplying a foreign Store id. Per ADR 004, application-layer scoping is
     * the *only* enforcement mechanism (there is no RLS backstop), so the test must
     * actually exercise that specific check, not stop one step short of it.
     */
    @Test
    void ownerOfAnotherOrganizationCannotReadAForeignStore() throws Exception {
        String ownerASubject = provisionedSubject();
        UUID organizationAId = createOrganization(ownerASubject, "Org A");
        UUID storeAId = createStore(ownerASubject, organizationAId, "org-a-store-" + UUID.randomUUID(), "EGP");

        String ownerBSubject = provisionedSubject();
        UUID organizationBId = createOrganization(ownerBSubject, "Org B");

        mockMvc.perform(get("/api/organizations/{orgId}/stores/{storeId}", organizationBId, storeAId)
                        .with(jwtSubject(ownerBSubject)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerOfAnotherOrganizationCannotCreateAStoreInAForeignOrganization() throws Exception {
        String ownerASubject = provisionedSubject();
        UUID organizationAId = createOrganization(ownerASubject, "Org A");

        String ownerBSubject = provisionedSubject();
        createOrganization(ownerBSubject, "Org B");

        mockMvc.perform(post("/api/organizations/{orgId}/stores", organizationAId)
                        .with(jwtSubject(ownerBSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Intruder Store",
                                "slug", "intruder-store-" + UUID.randomUUID(),
                                "currencyCode", "EGP"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerOfAnotherOrganizationCannotGrantStoreAccessOnAForeignStore() throws Exception {
        String ownerASubject = provisionedSubject();
        UUID organizationAId = createOrganization(ownerASubject, "Org A");
        UUID storeAId = createStore(ownerASubject, organizationAId, "org-a-store-" + UUID.randomUUID(), "EGP");

        String ownerBSubject = provisionedSubject();
        UUID organizationBId = createOrganization(ownerBSubject, "Org B");
        UUID ownerBUserAccountId = userAccountId(ownerBSubject);

        mockMvc.perform(post("/api/organizations/{orgId}/stores/{storeId}/store-access", organizationBId, storeAId)
                        .with(jwtSubject(ownerBSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userAccountId", ownerBUserAccountId.toString(),
                                "role", "CATALOG_EDITOR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberCannotGrantStoreAccess() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org D");
        UUID storeId = createStore(ownerSubject, organizationId, "org-d-store-" + UUID.randomUUID(), "EGP");

        String memberSubject = provisionedSubject();
        UUID memberUserAccountId = userAccountId(memberSubject);
        TenancyFixtures.addMembership(jdbcTemplate, memberUserAccountId, organizationId, "MEMBER");

        mockMvc.perform(post("/api/organizations/{orgId}/stores/{storeId}/store-access", organizationId, storeId)
                        .with(jwtSubject(memberSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userAccountId", memberUserAccountId.toString(),
                                "role", "CATALOG_EDITOR"))))
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
