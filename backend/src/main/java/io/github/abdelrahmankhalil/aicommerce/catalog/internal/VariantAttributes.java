package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

import java.util.Map;

/**
 * Normalizes a ProductVariant's client-supplied {@code attributes} map: never null
 * (defaults to empty), and every key/value must be non-blank/non-null - nested
 * structures and arrays are already rejected earlier, while deserializing the
 * request body into {@code Map<String,String>}. Duplicate attribute combinations
 * within the same Product are enforced by the database (see {@code V2__catalog.sql}),
 * not here.
 */
final class VariantAttributes {

    private VariantAttributes() {
    }

    static Map<String, String> normalize(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                throw new InvalidAttributesException();
            }
        }
        return Map.copyOf(attributes);
    }
}
