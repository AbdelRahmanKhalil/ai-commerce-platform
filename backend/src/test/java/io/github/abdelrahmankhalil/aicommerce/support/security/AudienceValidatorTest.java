package io.github.abdelrahmankhalil.aicommerce.support.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AudienceValidatorTest {

    private final AudienceValidator validator = new AudienceValidator("aicommerce-api");

    @Test
    void acceptsATokenWithTheExpectedAudience() {
        Jwt jwt = jwtWithAudience(List.of("aicommerce-api"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rejectsATokenWithAnUnrelatedAudience() {
        Jwt jwt = jwtWithAudience(List.of("some-other-client"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void rejectsATokenWithNoAudienceAtAll() {
        Jwt jwt = jwtWithAudience(List.of());

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isTrue();
    }

    private static Jwt jwtWithAudience(List<String> audience) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("sub", "some-subject")
                .audience(audience)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
