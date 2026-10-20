package io.github.abdelrahmankhalil.aicommerce.tenancy;

import tools.jackson.databind.ObjectMapper;
import io.github.abdelrahmankhalil.aicommerce.support.AbstractIntegrationTest;
import io.github.abdelrahmankhalil.aicommerce.support.TenancyFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR 008 PR A.1: Store creation must reject a deployment-configured reserved slug
 * through the real HTTP surface, with a sanitized 400 that never echoes the rejected
 * slug or any persistence detail - and must never let a reserved slug leak that policy
 * to a caller who isn't even authorized to create a Store in that Organization (a
 * reserved-slug attempt from an unauthorized caller must still be a plain 403).
 */
class ReservedStoreSlugIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @ValueSource(strings = {"www", "api", "app", "admin"})
    void ownerCannotCreateAStoreWithADefaultReservedSlug(String reservedSlug) throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Reserved Slug Org Owner " + reservedSlug);

        String responseJson = mockMvc.perform(post("/api/organizations/{orgId}/stores", organizationId)
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Reserved Attempt",
                                "slug", reservedSlug,
                                "currencyCode", "EGP"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Store slug"))
                .andExpect(jsonPath("$.detail").value("This Store slug is reserved and cannot be used."))
                .andReturn().getResponse().getContentAsString();

        // Sanitization: the rejected slug itself must never be echoed back in the
        // title/detail fields (the "instance" field legitimately contains the request
        // path, which may itself contain "/api/" - that is not a slug echo, so the
        // check is scoped to title/detail only, not the whole raw body). No SQL,
        // exception class name, or stack trace frame may appear anywhere in the body.
        String title = objectMapper.readTree(responseJson).get("title").asText();
        String detail = objectMapper.readTree(responseJson).get("detail").asText();
        assertThat(title).doesNotContainIgnoringCase(reservedSlug);
        assertThat(detail).doesNotContainIgnoringCase(reservedSlug);
        assertThat(responseJson)
                .doesNotContain("SELECT")
                .doesNotContain("INSERT")
                .doesNotContain("Exception")
                .doesNotContain("\tat ");
    }

    @Test
    void adminCannotCreateAStoreWithAReservedSlug() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Reserved Slug Org Admin");

        String adminSubject = provisionedSubject();
        UUID adminUserAccountId = userAccountId(adminSubject);
        TenancyFixtures.addMembership(jdbcTemplate, adminUserAccountId, organizationId, "ADMIN");

        mockMvc.perform(post("/api/organizations/{orgId}/stores", organizationId)
                        .with(jwtSubject(adminSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Reserved Attempt",
                                "slug", "api",
                                "currencyCode", "EGP"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Store slug"));
    }

    @Test
    void ownerCanCreateAStoreWithANormalUnreservedSlug() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Normal Slug Org");

        mockMvc.perform(post("/api/organizations/{orgId}/stores", organizationId)
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Normal Store",
                                "slug", "normal-store-" + UUID.randomUUID(),
                                "currencyCode", "EGP"))))
                .andExpect(status().isCreated());
    }

    /**
     * Authorization must run before the reserved-slug check: an unauthorized caller
     * (here, an OWNER of a wholly separate Organization) attempting a reserved slug
     * must get the same plain 403 as any other unauthorized create attempt - never a
     * 400 that would disclose the reserved-slug policy to someone who isn't even
     * authorized to create a Store in that Organization.
     */
    @Test
    void unauthorizedCallerAttemptingAReservedSlugGetsForbiddenNotBadRequest() throws Exception {
        String ownerASubject = provisionedSubject();
        UUID organizationAId = createOrganization(ownerASubject, "Reserved Slug Org A");

        String ownerBSubject = provisionedSubject();
        createOrganization(ownerBSubject, "Reserved Slug Org B");

        mockMvc.perform(post("/api/organizations/{orgId}/stores", organizationAId)
                        .with(jwtSubject(ownerBSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Intruder Reserved Attempt",
                                "slug", "api",
                                "currencyCode", "EGP"))))
                .andExpect(status().isForbidden());
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
}
