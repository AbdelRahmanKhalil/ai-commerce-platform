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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

        // A CyclicBarrier forces both threads to actually call findOrCreateUserAccount
        // at the same instant, rather than merely being submitted to the same pool -
        // without it, the pool could just run them back-to-back and never race at all.
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<UUID> provision = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return provisioningService.findOrCreateUserAccount(subject);
            };
            Future<UUID> first = executor.submit(provision);
            Future<UUID> second = executor.submit(provision);

            UUID firstId = first.get(10, TimeUnit.SECONDS);
            UUID secondId = second.get(10, TimeUnit.SECONDS);

            assertThat(firstId).isEqualTo(secondId);
            assertThat(TenancyFixtures.countUserAccountsBySubject(jdbcTemplate, subject)).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void concurrentHttpProvisioningResultsInExactlyOneUserAccount() throws Exception {
        String subject = "subject-" + UUID.randomUUID();

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<String> provisionViaHttp = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/me").with(jwtSubject(subject)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();
            };
            Future<String> first = executor.submit(provisionViaHttp);
            Future<String> second = executor.submit(provisionViaHttp);

            String firstResponse = first.get(10, TimeUnit.SECONDS);
            String secondResponse = second.get(10, TimeUnit.SECONDS);

            assertThat(firstResponse).isEqualTo(secondResponse);
            assertThat(TenancyFixtures.countUserAccountsBySubject(jdbcTemplate, subject)).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }
}
