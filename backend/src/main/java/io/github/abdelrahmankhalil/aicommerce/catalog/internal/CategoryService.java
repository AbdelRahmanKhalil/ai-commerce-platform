package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

import io.github.abdelrahmankhalil.aicommerce.tenancy.TenancyAuthorization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Single responsibility: Category lifecycle. Authorization is checked first, inside
 * each method's own transaction boundary, via tenancy's public
 * {@code TenancyAuthorization} API - catalog never re-implements Organization-wide
 * OWNER/ADMIN logic itself (ADR 006).
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TenancyAuthorization tenancyAuthorization;

    CategoryService(CategoryRepository categoryRepository, TenancyAuthorization tenancyAuthorization) {
        this.categoryRepository = categoryRepository;
        this.tenancyAuthorization = tenancyAuthorization;
    }

    @Transactional
    public UUID create(UUID organizationId, UUID storeId, UUID userAccountId, String name, String slug) {
        tenancyAuthorization.requireStoreAccess(userAccountId, organizationId, storeId, CatalogRoles.WRITE);
        Category category = categoryRepository.save(new Category(organizationId, storeId, name, slug));
        return category.getId();
    }

    @Transactional(readOnly = true)
    public Page<Category> list(UUID organizationId, UUID storeId, UUID userAccountId, int page, int size) {
        tenancyAuthorization.requireStoreAccess(userAccountId, organizationId, storeId, CatalogRoles.READ);
        return categoryRepository.findByStoreIdAndOrganizationId(storeId, organizationId,
                CatalogPaging.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
    }

    @Transactional
    public void update(UUID organizationId, UUID storeId, UUID categoryId, UUID userAccountId, String name,
                        String slug) {
        tenancyAuthorization.requireStoreAccess(userAccountId, organizationId, storeId, CatalogRoles.WRITE);
        Category category = categoryRepository.findByIdAndStoreIdAndOrganizationId(categoryId, storeId, organizationId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
        category.rename(name, slug);
    }

    /**
     * Hard-deletes the Category. {@code product_category} association rows cascade
     * at the database level (see {@code V2__catalog.sql}); Products themselves are
     * never touched.
     */
    @Transactional
    public void delete(UUID organizationId, UUID storeId, UUID categoryId, UUID userAccountId) {
        tenancyAuthorization.requireStoreAccess(userAccountId, organizationId, storeId, CatalogRoles.WRITE);
        Category category = categoryRepository.findByIdAndStoreIdAndOrganizationId(categoryId, storeId, organizationId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
        categoryRepository.delete(category);
    }
}
