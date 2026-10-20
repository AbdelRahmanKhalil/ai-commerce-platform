package io.github.abdelrahmankhalil.aicommerce.tenancy.internal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain unit tests (no Spring context) proving {@link ReservedStoreSlugPolicy} enforces
 * whatever set its {@link ReservedStoreSlugProperties} was configured with - not a
 * hardcoded list of names - and that the comparison is case-insensitive.
 */
class ReservedStoreSlugPolicyTest {

    @Test
    void rejectsASlugFromItsOwnCustomConfiguredSet() {
        ReservedStoreSlugPolicy policy = new ReservedStoreSlugPolicy(
                new ReservedStoreSlugProperties(List.of("reserved-custom")));

        assertThatThrownBy(() -> policy.requireAvailable("reserved-custom"))
                .isInstanceOf(ReservedStoreSlugException.class);
    }

    @Test
    void allowsASlugNotInItsOwnCustomConfiguredSetEvenIfReservedElsewhere() {
        ReservedStoreSlugPolicy policy = new ReservedStoreSlugPolicy(
                new ReservedStoreSlugProperties(List.of("reserved-custom")));

        // "api" is one of the default reserved slugs shipped in application.yml, but is
        // not in *this* policy instance's configured set - proving the policy is driven
        // entirely by its configured properties, not by any hardcoded name.
        assertThatCode(() -> policy.requireAvailable("api")).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnUppercaseCaseBypassOfAConfiguredReservedSlug() {
        ReservedStoreSlugPolicy policy = new ReservedStoreSlugPolicy(
                new ReservedStoreSlugProperties(List.of("api")));

        assertThatThrownBy(() -> policy.requireAvailable("API"))
                .isInstanceOf(ReservedStoreSlugException.class);
    }
}
