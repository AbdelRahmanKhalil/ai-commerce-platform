package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

import io.github.abdelrahmankhalil.aicommerce.catalog.CatalogVariantDirectory;
import io.github.abdelrahmankhalil.aicommerce.catalog.VariantReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Single responsibility: Catalog's implementation of its own public
 * {@link CatalogVariantDirectory} API (ADR 007). A Variant is always looked up by
 * (id, storeId, organizationId) together - never by id alone - so a foreign Store or
 * Organization reference fails safely and uniformly as "not found," never revealing
 * that the id exists elsewhere.
 */
@Service
class CatalogVariantDirectoryService implements CatalogVariantDirectory {

    private final ProductVariantRepository variantRepository;

    CatalogVariantDirectoryService(ProductVariantRepository variantRepository) {
        this.variantRepository = variantRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VariantReference> findVariant(UUID organizationId, UUID storeId, UUID variantId) {
        return variantRepository.findByIdAndStoreIdAndOrganizationId(variantId, storeId, organizationId)
                .map(variant -> new VariantReference(variant.getId(), variant.getOrganizationId(), variant.getStoreId()));
    }
}
