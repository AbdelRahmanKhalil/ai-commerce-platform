package io.github.abdelrahmankhalil.aicommerce.support.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects a JWT whose {@code aud} claim does not contain this API's expected audience.
 * Without this, any token issued by the shared Keycloak realm - regardless of which
 * client it was minted for - would be accepted here, since the default resource-server
 * setup only validates the issuer, signature, and expiry (see ADR 003: the realm is
 * shared across clients, so issuer alone does not identify "was this token meant for
 * this API").
 */
class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error(
            "invalid_token", "The required audience is missing", null);

    private final String expectedAudience;

    AudienceValidator(String expectedAudience) {
        this.expectedAudience = expectedAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (jwt.getAudience().contains(expectedAudience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
    }
}
