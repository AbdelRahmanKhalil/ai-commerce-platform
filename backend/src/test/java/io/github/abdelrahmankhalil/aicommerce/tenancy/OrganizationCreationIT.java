package io.github.abdelrahmankhalil.aicommerce.tenancy;

import tools.jackson.databind.ObjectMapper;
import io.github.abdelrahmankhalil.aicommerce.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
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

class OrganizationCreationIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void creatingAnOrganizationAtomicallyGrantsOwnerMembership() throws Exception {
        String subject = "subject-" + UUID.randomUUID();
        mockMvc.perform(post("/api/me").with(jwtSubject(subject))).andExpect(status().isOk());

        String responseJson = mockMvc.perform(post("/api/organizations")
                        .with(jwtSubject(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Trading"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").exists())
                .andReturn().getResponse().getContentAsString();

        UUID organizationId = UUID.fromString(objectMapper.readTree(responseJson).get("organizationId").asText());

        String role = jdbcTemplate.queryForObject(
                """
                SELECT m.role FROM membership m
                JOIN user_account u ON u.id = m.user_account_id
                WHERE m.organization_id = ? AND u.keycloak_subject = ?
                """,
                String.class, organizationId, subject);

        assertThat(role).isEqualTo("OWNER");
    }
}
