package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

import io.github.abdelrahmankhalil.aicommerce.tenancy.TenancyAuthorization;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Single responsibility: ProductVariant lifecycle beyond a Product's initial
 * creation. A Variant is always looked up by (id, productId, storeId,
 * organizationId) together - never by id alone - so a foreign Product/Variant
 * combination fails safely (ADR 006). Variant rows are never hard-deleted; status
 * simply moves between ACTIVE and INACTIVE.
 */
@Service
public class ProductVariantService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final TenancyAuthorization tenancyAuthorization;

    ProductVariantService(ProductRepository productRepository, ProductVariantRepository variantRepository,
                           TenancyAuthorization tenancyAuthorization) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.tenancyAuthorization = tenancyAuthorization;
    }

    @Transactional
    public UUID addVariant(UUID organizationId, UUID storeId, UUID productId, UUID userAccountId, String sku,
                            VariantStatus status, BigDecimal price, Map<String, String> attributes) {
        tenancyAuthorization.requireStoreAccess(userAccountId, organizationId, storeId, CatalogRoles.WRITE);
        requireProduct(organizationId, storeId, productId);

        ProductVariant variant = variantRepository.save(new ProductVariant(organizationId, storeId, productId,
                sku.trim(), status, price, VariantAttributes.normalize(attributes)));
        return variant.getId();
    }

    @Transactional
    public void updateVariant(UUID organizationId, UUID storeId, UUID productId, UUID variantId, UUID userAccountId,
                               String sku, BigDecimal price, Map<String, String> attributes, VariantStatus status) {
        tenancyAuthorization.requireStoreAccess(userAccountId, organizationId, storeId, CatalogRoles.WRITE);
        requireProduct(organizationId, storeId, productId);

        ProductVariant variant = variantRepository
                .findByIdAndProductIdAndStoreIdAndOrganizationId(variantId, productId, storeId, organizationId)
                .orElseThrow(() -> new VariantNotFoundException(variantId));

        variant.update(sku.trim(), price, VariantAttributes.normalize(attributes), status);
    }

    private void requireProduct(UUID organizationId, UUID storeId, UUID productId) {
        productRepository.findByIdAndStoreIdAndOrganizationId(productId, storeId, organizationId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
