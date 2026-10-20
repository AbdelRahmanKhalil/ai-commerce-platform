package io.github.abdelrahmankhalil.aicommerce.tenancy.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Enforces the deployment-configured reserved Store slug set (ADR 008) against a Store
 * creation candidate. Deliberately narrow: the only normalization performed here is
 * case/whitespace normalization of the *candidate being compared*, solely so the
 * reserved-slug check itself cannot be bypassed by a case difference (e.g. a configured
 * {@code "api"} must still reject a direct caller passing {@code "API"}). This is not
 * general Store slug normalization and never rewrites the slug passed on to
 * persistence - see {@code StoreManagementService.createStore}.
 */
@Component
@EnableConfigurationProperties(ReservedStoreSlugProperties.class)
class ReservedStoreSlugPolicy {

    private final ReservedStoreSlugProperties properties;

    ReservedStoreSlugPolicy(ReservedStoreSlugProperties properties) {
        this.properties = properties;
    }

    void requireAvailable(String slug) {
        String candidate = slug.trim().toLowerCase(Locale.ROOT);
        if (properties.reservedStoreSlugs().contains(candidate)) {
            throw new ReservedStoreSlugException();
        }
    }
}
