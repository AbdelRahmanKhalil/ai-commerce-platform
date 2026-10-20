package io.github.abdelrahmankhalil.aicommerce.tenancy.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deployment-configured reserved Store slugs (ADR 008: {@code Store.slug} is now a real
 * routable DNS label under the hosted storefront's {@code <slug>.<base-domain>} scheme,
 * so a small set of labels - e.g. {@code www}/{@code api}/{@code app}/{@code admin} -
 * must never be assignable to a merchant Store). The reserved set is environment-
 * specific configuration (not a permanent hardcoded list, not a database constraint -
 * see ADR 008), bound from {@code app.tenancy.reserved-store-slugs}.
 * <p>
 * Validation/canonicalization happens eagerly in the constructor, at configuration-
 * binding time, so a malformed entry fails application startup with an
 * {@link IllegalArgumentException} rather than being silently ignored at runtime: every
 * entry is trimmed, lowercased ({@link Locale#ROOT}), rejected if blank, validated
 * against the exact same DNS-label grammar already enforced on {@code Store.slug}
 * itself, and deduplicated. The resulting canonical set must be non-empty.
 */
@ConfigurationProperties(prefix = "app.tenancy")
class ReservedStoreSlugProperties {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$");

    private final Set<String> reservedStoreSlugs;

    ReservedStoreSlugProperties(Collection<String> reservedStoreSlugs) {
        if (reservedStoreSlugs == null) {
            throw new IllegalArgumentException("app.tenancy.reserved-store-slugs must be configured");
        }

        Set<String> canonical = new LinkedHashSet<>();
        for (String entry : reservedStoreSlugs) {
            if (entry == null || entry.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "app.tenancy.reserved-store-slugs entries must not be blank");
            }
            String canonicalEntry = entry.trim().toLowerCase(Locale.ROOT);
            if (!SLUG_PATTERN.matcher(canonicalEntry).matches()) {
                throw new IllegalArgumentException(
                        "app.tenancy.reserved-store-slugs entry is not a valid DNS label: '" + canonicalEntry + "'");
            }
            canonical.add(canonicalEntry);
        }

        if (canonical.isEmpty()) {
            throw new IllegalArgumentException(
                    "app.tenancy.reserved-store-slugs must contain at least one valid entry");
        }

        this.reservedStoreSlugs = Set.copyOf(canonical);
    }

    /** The canonical (trimmed, lowercased, deduplicated) configured reserved slugs. */
    Set<String> reservedStoreSlugs() {
        return reservedStoreSlugs;
    }
}
