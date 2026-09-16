package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

import io.github.abdelrahmankhalil.aicommerce.tenancy.TenancyAuthorization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Single responsibility: Product lifecycle. Every Product is created with at least
 * one initial Variant, its selected Category associations, and any initial Images,
 * atomically in one transaction (ADR 006) - a failure anywhere in that transaction
 * rolls back the whole Product. Product update fully replaces its Category
 * associations and Images in the same transaction as its field update.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final TenancyAuthorization tenancyAuthorization;

    ProductService(ProductRepository productRepository,
                    ProductVariantRepository variantRepository,
                    ProductCategoryRepository productCategoryRepository,
                    ProductImageRepository productImageRepository,
                    CategoryRepository categoryRepository,
                    TenancyAuthorization tenancyAuthorization) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.productImageRepository = productImageRepository;
        this.categoryRepository = categoryRepository;
        this.tenancyAuthorization = tenancyAuthorization;
    }

    @Transactional
    public UUID createProduct(UUID organizationId, UUID storeId, UUID userAccountId, String title, String description,
                               String slug, ProductStatus status, List<UUID> categoryIds, List<VariantInput> variants,
                               List<ImageInput> images) {
        tenancyAuthorization.requireStoreAccess(userAccountId, organizationId, storeId, CatalogRoles.WRITE);

        Set<UUID> distinctCategoryIds = new LinkedHashSet<>(categoryIds);
        requireCategoriesBelongToStore(organizationId, storeId, distinctCategoryIds);

        Product product = productRepository.save(new Product(organizationId, storeId, title, description, slug, status));

        for (VariantInput variant : variants) {
            variantRepository.save(new ProductVariant(organizationId, storeId, product.getId(), variant.sku().trim(),
                    variant.status(), variant.price(), VariantAttributes.normalize(variant.attributes())));
        }

        for (UUID categoryId : distinctCategoryIds) {
            productCategoryRepository.save(new ProductCategory(organizationId, storeId, product.getId(), categoryId));
        }

        int position = 0;
        for (ImageInput image : images) {
            productImageRepository.save(
                    new ProductImage(organizationId, storeId, product.getId(), image.url(), image.altText(), position++));
        }

        return product.getId();
    }

    @Transactional(readOnly = true)
    public ProductDetail getProductDetail(UUID organizationId, UUID storeId, UUID userAccountId, UUID productId) {
        tenancyAuthorization.requireStoreAccess(userAccountId, organizationId, storeId, CatalogRoles.READ);
        Product product = productRepository.findByIdAndStoreIdAndOrganizationId(productId, storeId, organizationId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        List<ProductVariant> variants = variantRepository.findByProductIdAndStoreIdAndOrganizationId(productId, storeId,
                organizationId);
        List<ProductImage> images = productImageRepository.findByProductIdAndStoreIdAndOrganizationIdOrderByPositionAsc(
                productId, storeId, organizationId);
        List<UUID> categoryIds = productCategoryRepository.findByProductIdAndStoreIdAndOrganizationId(productId, storeId,
                        organizationId)
                .stream()
                .map(ProductCategory::getCategoryId)
                .toList();

        return new ProductDetail(product, variants, images, categoryIds);
    }

    @Transactional(readOnly = true)
    public Page<Product> listProducts(UUID organizationId, UUID storeId, UUID userAccountId, ProductStatus status,
                                        int page, int size) {
        tenancyAuthorization.requireStoreAccess(userAccountId, organizationId, storeId, CatalogRoles.READ);
        var pageable = CatalogPaging.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return status == null
                ? productRepository.findByStoreIdAndOrganizationId(storeId, organizationId, pageable)
                : productRepository.findByStoreIdAndOrganizationIdAndStatus(storeId, organizationId, status, pageable);
    }

    /**
     * Full replacement of Product-editable state: field update, plus a complete
     * replace of both Category associations and Images. Variants are never touched
     * here - they have their own endpoints/service.
     */
    @Transactional
    public void updateProduct(UUID organizationId, UUID storeId, UUID productId, UUID userAccountId, String title,
                               String description, String slug, ProductStatus status, List<UUID> categoryIds,
                               List<ImageInput> images) {
        tenancyAuthorization.requireStoreAccess(userAccountId, organizationId, storeId, CatalogRoles.WRITE);
        Product product = productRepository.findByIdAndStoreIdAndOrganizationId(productId, storeId, organizationId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        Set<UUID> distinctCategoryIds = new LinkedHashSet<>(categoryIds);
        requireCategoriesBelongToStore(organizationId, storeId, distinctCategoryIds);

        product.updateDetails(title, description, slug, status);

        // Flush each delete before inserting its replacement rows: Hibernate's default
        // flush ordering issues all pending inserts before pending deletes, which would
        // otherwise let a replacement row collide with an old row (e.g. the same
        // Category re-selected, or image position 0 reused) still physically present.
        productCategoryRepository.deleteByProductIdAndStoreIdAndOrganizationId(productId, storeId, organizationId);
        productCategoryRepository.flush();
        for (UUID categoryId : distinctCategoryIds) {
            productCategoryRepository.save(new ProductCategory(organizationId, storeId, productId, categoryId));
        }

        productImageRepository.deleteByProductIdAndStoreIdAndOrganizationId(productId, storeId, organizationId);
        productImageRepository.flush();
        int position = 0;
        for (ImageInput image : images) {
            productImageRepository.save(
                    new ProductImage(organizationId, storeId, productId, image.url(), image.altText(), position++));
        }
    }

    /**
     * Every referenced Category must genuinely belong to the requested Store/
     * Organization - a Category id from another Store or Organization is rejected
     * cleanly here rather than relying solely on the database's composite foreign
     * key backstop.
     */
    private void requireCategoriesBelongToStore(UUID organizationId, UUID storeId, Set<UUID> categoryIds) {
        if (categoryIds.isEmpty()) {
            return;
        }
        long matching = categoryRepository.countByIdInAndStoreIdAndOrganizationId(categoryIds, storeId, organizationId);
        if (matching != categoryIds.size()) {
            throw new CategoryNotFoundException(categoryIds.iterator().next());
        }
    }
}
