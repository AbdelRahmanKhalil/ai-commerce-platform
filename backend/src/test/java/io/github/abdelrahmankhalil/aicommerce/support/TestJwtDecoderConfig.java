package io.github.abdelrahmankhalil.aicommerce.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Application authorization tests never need a real Keycloak: requests are
 * authenticated via Spring Security Test's {@code jwt()} request post-processor, which
 * injects an already-authenticated principal without ever calling a {@link JwtDecoder}.
 * A bean still has to exist for the resource-server filter chain to build, so this is a
 * stub that fails loudly if it is ever actually invoked.
 */
@TestConfiguration
public class TestJwtDecoderConfig {

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            throw new UnsupportedOperationException(
                    "JwtDecoder should never be invoked in tests - authenticate requests with "
                            + "SecurityMockMvcRequestPostProcessors.jwt() instead.");
        };
    }
}
