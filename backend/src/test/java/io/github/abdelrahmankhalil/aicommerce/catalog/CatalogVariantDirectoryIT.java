package io.github.abdelrahmankhalil.aicommerce.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The CATALOG PUBLIC API cases required by the task brief (ADR 007):
 * {@code CatalogVariantDirectory.findVariant} returns the correct scoped Variant,
 * {@code Optional.empty()} uniformly for nonexistent/foreign-Store/foreign-Organization
 * Variants, still resolves an INACTIVE Variant, and leaks nothing beyond {@link
 * VariantReference}'s three fields.
 */
class CatalogVariantDirectoryIT extends AbstractCatalogIntegrationTest {

    @Autowired
    private CatalogVariantDirectory catalogVariantDirectory;

    @Test
    void resolvesAVariantInTheCorrectScope() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Directory Found");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID variantId = createVariant(ownerSubject, organizationId, storeId, "ACTIVE");

        Optional<VariantReference> result = catalogVariantDirectory.findVariant(organizationId, storeId, variantId);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(variantId);
        assertThat(result.get().organizationId()).isEqualTo(organizationId);
        assertThat(result.get().storeId()).isEqualTo(storeId);
    }

    @Test
    void returnsEmptyForANonexistentVariant() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Directory Missing");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        Optional<VariantReference> result =
                catalogVariantDirectory.findVariant(organizationId, storeId, UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyForAVariantFromAnotherStore() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Directory Foreign Store");
        UUID storeAId = createStore(ownerSubject, organizationId, "store-a-" + UUID.randomUUID(), "EGP");
        UUID storeBId = createStore(ownerSubject, organizationId, "store-b-" + UUID.randomUUID(), "EGP");
        UUID variantInStoreA = createVariant(ownerSubject, organizationId, storeAId, "ACTIVE");

        Optional<VariantReference> result =
                catalogVariantDirectory.findVariant(organizationId, storeBId, variantInStoreA);

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyForAVariantFromAnotherOrganization() throws Exception {
        String ownerASubject = provisionedSubject();
        UUID organizationAId = createOrganization(ownerASubject, "Org Directory A");
        UUID storeAId = createStore(ownerASubject, organizationAId, "store-a-" + UUID.randomUUID(), "EGP");
        UUID variantInOrgA = createVariant(ownerASubject, organizationAId, storeAId, "ACTIVE");

        String ownerBSubject = provisionedSubject();
        UUID organizationBId = createOrganization(ownerBSubject, "Org Directory B");

        Optional<VariantReference> result =
                catalogVariantDirectory.findVariant(organizationBId, storeAId, variantInOrgA);

        assertThat(result).isEmpty();
    }

    @Test
    void resolvesAnInactiveVariant() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Directory Inactive");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID variantId = createVariant(ownerSubject, organizationId, storeId, "INACTIVE");

        Optional<VariantReference> result = catalogVariantDirectory.findVariant(organizationId, storeId, variantId);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(variantId);
    }

    @Test
    void variantReferenceExposesOnlyScopedIdentityFields() {
        Field[] fields = VariantReference.class.getDeclaredFields();
        assertThat(fields).extracting(Field::getName).containsExactlyInAnyOrder("id", "organizationId", "storeId");
    }

    /** Creates a Product with a single Variant of the given status, returning the Variant id. */
    private UUID createVariant(String subject, UUID organizationId, UUID storeId, String variantStatus)
            throws Exception {
        Map<String, Object> body = Map.of(
                "title", "Directory Product " + UUID.randomUUID(),
                "slug", "directory-product-" + UUID.randomUUID(),
                "status", "ACTIVE",
                "categoryIds", List.of(),
                "variants", List.of(Map.of(
                        "sku", "SKU-" + UUID.randomUUID(),
                        "status", variantStatus,
                        "price", new BigDecimal("9.99").toString(),
                        "attributes", Map.of())),
                "images", List.of());

        String json = mockMvc.perform(post(productsUrl(organizationId, storeId))
                        .with(jwtSubject(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID productId = UUID.fromString(objectMapper.readTree(json).get("productId").asText());

        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id FROM product_variant WHERE product_id = ?", String.class, productId));
    }
}
