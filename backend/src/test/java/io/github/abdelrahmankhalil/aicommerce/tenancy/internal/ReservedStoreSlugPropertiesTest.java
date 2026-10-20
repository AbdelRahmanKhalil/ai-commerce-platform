package io.github.abdelrahmankhalil.aicommerce.tenancy.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain unit tests (no Spring context) of {@link ReservedStoreSlugProperties}'
 * construction-time normalization and validation.
 */
class ReservedStoreSlugPropertiesTest {

    @Test
    void trimsWhitespaceAndLowercasesEntries() {
        ReservedStoreSlugProperties properties = new ReservedStoreSlugProperties(List.of(" API "));

        assertThat(properties.reservedStoreSlugs()).containsExactly("api");
    }

    @Test
    void deduplicatesCaseAndWhitespaceVariantsOfTheSameEntry() {
        ReservedStoreSlugProperties properties = new ReservedStoreSlugProperties(List.of("API", "api", " api "));

        assertThat(properties.reservedStoreSlugs()).containsExactly("api");
    }

    @Test
    void acceptsAMultiEntryConfiguredSet() {
        ReservedStoreSlugProperties properties = new ReservedStoreSlugProperties(List.of("www", "api", "app", "admin"));

        assertThat(properties.reservedStoreSlugs()).containsExactlyInAnyOrder("www", "api", "app", "admin");
    }

    @Test
    void rejectsANullConfiguredCollection() {
        assertThatThrownBy(() -> new ReservedStoreSlugProperties(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsABlankEntry(String blankEntry) {
        assertThatThrownBy(() -> new ReservedStoreSlugProperties(List.of(blankEntry)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"foo.bar", "-api", "api-"})
    void rejectsAnEntryViolatingTheDnsLabelGrammar(String malformedEntry) {
        assertThatThrownBy(() -> new ReservedStoreSlugProperties(List.of(malformedEntry)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnEmptyConfiguredCollection() {
        assertThatThrownBy(() -> new ReservedStoreSlugProperties(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
