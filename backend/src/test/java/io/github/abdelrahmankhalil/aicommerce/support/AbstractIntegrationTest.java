package io.github.abdelrahmankhalil.aicommerce.support;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Shared base for tenancy integration tests: a real Postgres via Testcontainers, a full
 * Spring MVC context with security enabled, and mocked JWT authentication - no real
 * Keycloak needed, per the milestone brief.
 * <p>
 * The container is started once, manually, as a true singleton shared by every
 * subclass across the whole test run (Testcontainers' "singleton container" pattern) -
 * deliberately NOT using {@code @Testcontainers}/{@code @Container}, whose per-class
 * stop() lifecycle would tear the container down after the first test class finishes
 * while Spring's cached ApplicationContext (reused across classes with identical
 * configuration) keeps pointing at its now-dead port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestJwtDecoderConfig.class)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6");

    static {
        POSTGRES.start();
    }

    protected static RequestPostProcessor jwtSubject(String subject) {
        return jwt().jwt(builder -> builder.subject(subject));
    }
}
