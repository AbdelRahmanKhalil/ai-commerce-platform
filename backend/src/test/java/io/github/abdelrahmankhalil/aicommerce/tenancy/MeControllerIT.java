package io.github.abdelrahmankhalil.aicommerce.tenancy;

import io.github.abdelrahmankhalil.aicommerce.support.AbstractIntegrationTest;
import io.github.abdelrahmankhalil.aicommerce.support.TenancyFixtures;
import io.github.abdelrahmankhalil.aicommerce.tenancy.internal.UserAccountProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountProvisioningService provisioningService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void provisioningIsIdempotent() throws Exception {
        String subject = "subject-" + UUID.randomUUID();

        String firstId = mockMvc.perform(post("/api/me").with(jwtSubject(subject)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String secondId = mockMvc.perform(post("/api/me").with(jwtSubject(subject)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(firstId).isEqualTo(secondId);
        assertThat(TenancyFixtures.countUserAccountsBySubject(jdbcTemplate, subject)).isEqualTo(1);
    }

    @Test
    void concurrentProvisioningResultsInExactlyOneUserAccount() throws Exception {
        String subject = "subject-" + UUID.randomUUID();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<UUID> provision = () -> provisioningService.findOrCreateUserAccount(subject);
            Future<UUID> first = executor.submit(provision);
            Future<UUID> second = executor.submit(provision);

            UUID firstId = first.get();
            UUID secondId = second.get();

            assertThat(firstId).isEqualTo(secondId);
            assertThat(TenancyFixtures.countUserAccountsBySubject(jdbcTemplate, subject)).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }
}
