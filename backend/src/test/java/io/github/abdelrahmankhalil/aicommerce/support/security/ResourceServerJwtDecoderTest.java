package io.github.abdelrahmankhalil.aicommerce.support.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the actual configured resource-server decoder - the same
 * {@link ResourceServerConfig#tokenValidator(String, String)} composition wired onto
 * the real {@code JwtDecoder} bean - rejects a correctly-signed token with the wrong
 * audience, not just the standalone {@link AudienceValidator} in isolation (see
 * {@code AudienceValidatorTest}).
 * <p>
 * Uses a locally-generated RSA key pair and {@code NimbusJwtDecoder.withPublicKey(...)}
 * rather than a real or fake OIDC/Keycloak server: only the validator wiring is under
 * test here, not the JWKS-discovery mechanism itself, so no network call or mock HTTP
 * server is needed to prove it cleanly.
 */
class ResourceServerJwtDecoderTest {

    private static final String ISSUER = "https://issuer.example.com/realms/aicommerce";
    private static final String EXPECTED_AUDIENCE = "aicommerce-api";

    private RSAPrivateKey privateKey;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();

        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
        nimbusDecoder.setJwtValidator(ResourceServerConfig.tokenValidator(ISSUER, EXPECTED_AUDIENCE));
        decoder = nimbusDecoder;
    }

    @Test
    void acceptsATokenWithTheExpectedAudience() throws Exception {
        String token = signedToken(EXPECTED_AUDIENCE);

        assertThat(decoder.decode(token).getAudience()).contains(EXPECTED_AUDIENCE);
    }

    @Test
    void rejectsAnOtherwiseValidTokenWithTheWrongAudience() throws Exception {
        String token = signedToken("some-other-client");

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    private String signedToken(String audience) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(List.of(audience))
                .subject("some-subject")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(60)))
                .build();
        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        signedJwt.sign(new RSASSASigner(privateKey));
        return signedJwt.serialize();
    }
}
