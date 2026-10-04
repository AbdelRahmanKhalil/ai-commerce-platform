package io.github.abdelrahmankhalil.aicommerce.catalog;

import io.github.abdelrahmankhalil.aicommerce.catalog.internal.EmptyVariantsException;
import io.github.abdelrahmankhalil.aicommerce.catalog.internal.ProductService;
import io.github.abdelrahmankhalil.aicommerce.catalog.internal.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR 006: "every Product has at least one Variant" is an application invariant that
 * must be enforced by {@code ProductService} itself, at the write transaction
 * boundary - not merely by the controller's {@code @NotEmpty} Bean Validation. These
 * tests call {@code ProductService.createProduct} directly, bypassing the controller
 * and its request DTO entirely, to prove the guard holds for any caller - not only
 * ones that go through HTTP validation first.
 */
class ProductServiceInvariantIT extends AbstractCatalogIntegrationTest {

    @Autowired
    private ProductService productService;

    @Test
    void createProductRejectsEmptyVariantsWithoutPersistingAnyRows() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Service Invariant Empty");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID userAccountId = userAccountId(ownerSubject);

        assertThatThrownBy(() -> productService.createProduct(organizationId, storeId, userAccountId,
                "No Variant Product", null, "no-variant-product-direct", ProductStatus.ACTIVE, List.of(), List.of(),
                List.of()))
                .isInstanceOf(EmptyVariantsException.class);

        Long productCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE store_id = ?", Long.class, storeId);
        assertThat(productCount).isZero();

        Long variantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_variant WHERE store_id = ?", Long.class, storeId);
        assertThat(variantCount).isZero();
    }

    @Test
    void createProductRejectsNullVariantsWithoutPersistingAnyRows() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Service Invariant Null");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID userAccountId = userAccountId(ownerSubject);

        assertThatThrownBy(() -> productService.createProduct(organizationId, storeId, userAccountId,
                "Null Variant Product", null, "null-variant-product-direct", ProductStatus.ACTIVE, List.of(), null,
                List.of()))
                .isInstanceOf(EmptyVariantsException.class);

        Long productCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE store_id = ?", Long.class, storeId);
        assertThat(productCount).isZero();

        Long variantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_variant WHERE store_id = ?", Long.class, storeId);
        assertThat(variantCount).isZero();
    }
}
