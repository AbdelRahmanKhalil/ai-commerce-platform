package io.github.abdelrahmankhalil.aicommerce.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The DATABASE INTEGRITY cases required by the task brief: the composite foreign
 * keys in {@code V2__catalog.sql} are the defense-in-depth backstop rejecting
 * cross-Store/cross-Organization references even if application logic were ever
 * bypassed.
 */
class DatabaseIntegrityIT extends AbstractCatalogIntegrationTest {

    @Test
    void crossStoreProductCategoryLinkIsRejectedByPostgres() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org DB Integrity PC");
        UUID storeAId = createStore(ownerSubject, organizationId, "store-a-" + UUID.randomUUID(), "EGP");
        UUID storeBId = createStore(ownerSubject, organizationId, "store-b-" + UUID.randomUUID(), "EGP");
        UUID productInStoreA = createSimpleProduct(ownerSubject, organizationId, storeAId, "Product A", "product-a-pc");
        UUID categoryInStoreB = createCategory(ownerSubject, organizationId, storeBId, "Cat B", "cat-b-pc");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO product_category (id, organization_id, store_id, product_id, category_id, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), organizationId, storeAId, productInStoreA, categoryInStoreB, OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void productVariantCannotPointToProductFromAnotherStore() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org DB Integrity Variant");
        UUID storeAId = createStore(ownerSubject, organizationId, "store-a-" + UUID.randomUUID(), "EGP");
        UUID storeBId = createStore(ownerSubject, organizationId, "store-b-" + UUID.randomUUID(), "EGP");
        UUID productInStoreA = createSimpleProduct(ownerSubject, organizationId, storeAId, "Product A", "product-a-var");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO product_variant (id, organization_id, store_id, product_id, sku, status, price, "
                        + "attributes, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'CROSS-STORE', 'ACTIVE', 9.99, '{}'::jsonb, ?, ?)",
                UUID.randomUUID(), organizationId, storeBId, productInStoreA, OffsetDateTime.now(),
                OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void productImageCannotPointToProductFromAnotherStore() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org DB Integrity Image");
        UUID storeAId = createStore(ownerSubject, organizationId, "store-a-" + UUID.randomUUID(), "EGP");
        UUID storeBId = createStore(ownerSubject, organizationId, "store-b-" + UUID.randomUUID(), "EGP");
        UUID productInStoreA = createSimpleProduct(ownerSubject, organizationId, storeAId, "Product A", "product-a-img");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO product_image (id, organization_id, store_id, product_id, url, position, created_at) "
                        + "VALUES (?, ?, ?, ?, 'https://example.com/x.png', 0, ?)",
                UUID.randomUUID(), organizationId, storeBId, productInStoreA, OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void productVariantCannotPointToProductFromAnotherOrganization() throws Exception {
        String ownerASubject = provisionedSubject();
        UUID organizationAId = createOrganization(ownerASubject, "Org DB Integrity A");
        UUID storeAId = createStore(ownerASubject, organizationAId, "store-a-" + UUID.randomUUID(), "EGP");
        UUID productInOrgA = createSimpleProduct(ownerASubject, organizationAId, storeAId, "Product A", "product-a-org");

        String ownerBSubject = provisionedSubject();
        UUID organizationBId = createOrganization(ownerBSubject, "Org DB Integrity B");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO product_variant (id, organization_id, store_id, product_id, sku, status, price, "
                        + "attributes, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'CROSS-ORG', 'ACTIVE', 9.99, '{}'::jsonb, ?, ?)",
                UUID.randomUUID(), organizationBId, storeAId, productInOrgA, OffsetDateTime.now(), OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
