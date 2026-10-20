package io.github.abdelrahmankhalil.aicommerce.tenancy;

import tools.jackson.databind.ObjectMapper;
import io.github.abdelrahmankhalil.aicommerce.support.AbstractIntegrationTest;
import io.github.abdelrahmankhalil.aicommerce.tenancy.internal.ReservedStoreSlugException;
import io.github.abdelrahmankhalil.aicommerce.tenancy.internal.StoreManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR 008: the reserved Store slug policy is an application invariant that
 * {@code StoreManagementService} itself must enforce at the write transaction
 * boundary - not merely via the controller's request DTO. These tests call
 * {@code StoreManagementService.createStore} directly, bypassing
 * {@code StoreController}/Bean Validation entirely, to prove the guard holds for any
 * caller, and that it cannot be bypassed by a case difference the HTTP-layer DNS-label
 * pattern would otherwise have already lowercased away - mirroring
 * {@code catalog.ProductServiceInvariantIT}/{@code inventory.InventoryServiceInvariantIT}.
 */
class StoreManagementServiceInvariantIT extends AbstractIntegrationTest {

    @Autowired
    private StoreManagementService storeManagementService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createStoreRejectsAConfiguredReservedSlugWithoutPersistingAnyRow() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Service Invariant Reserved");
        UUID userAccountId = userAccountId(ownerSubject);

        assertThatThrownBy(() -> storeManagementService.createStore(
                organizationId, userAccountId, "Reserved Direct", "api", "EGP"))
                .isInstanceOf(ReservedStoreSlugException.class);

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM store WHERE organization_id = ?", Long.class, organizationId);
        assertThat(count).isZero();
    }

    @Test
    void createStoreRejectsAnUppercaseCaseBypassOfAConfiguredReservedSlugWithoutPersistingAnyRow() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Service Invariant Reserved Case");
        UUID userAccountId = userAccountId(ownerSubject);

        assertThatThrownBy(() -> storeManagementService.createStore(
                organizationId, userAccountId, "Reserved Direct Case", "API", "EGP"))
                .isInstanceOf(ReservedStoreSlugException.class);

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM store WHERE organization_id = ?", Long.class, organizationId);
        assertThat(count).isZero();
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
