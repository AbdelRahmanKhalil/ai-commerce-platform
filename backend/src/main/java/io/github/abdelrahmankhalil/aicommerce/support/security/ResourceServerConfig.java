package io.github.abdelrahmankhalil.aicommerce.support.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import io.github.abdelrahmankhalil.aicommerce.support.web.CorrelationIdFilter;

import java.util.List;

/**
 * Stateless OAuth2/JWT resource server. Every application endpoint under {@code /api}
 * requires a valid Keycloak-issued JWT; the actuator health endpoint stays public for
 * container orchestration checks. Authorization beyond "is this a valid token" (which
 * Organization/Store the caller may act on) is never decided here - see
 * {@code tenancy.TenancyAuthorization}.
 */
@Configuration
@EnableWebSecurity
public class ResourceServerConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CorrelationIdFilter correlationIdFilter) throws Exception {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .addFilterBefore(correlationIdFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Only created when an issuer-uri is actually configured (i.e. outside the default
     * test profile, where {@code support.TestJwtDecoderConfig} supplies a stub
     * decoder instead) - otherwise this bean and that stub would conflict.
     * <p>
     * The realm is shared across clients (ADR 003), so issuer validation alone does
     * not prove a token was meant for this API - see {@link AudienceValidator}.
     */
    @Bean
    @ConditionalOnProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri")
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${app.security.expected-audience}") String expectedAudience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new AudienceValidator(expectedAudience);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(withIssuer, withAudience)));
        return decoder;
    }
}
