package io.github.abdelrahmankhalil.aicommerce.support.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import io.github.abdelrahmankhalil.aicommerce.support.web.CorrelationIdFilter;

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
}
