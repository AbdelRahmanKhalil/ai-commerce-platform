/**
 * Catalog module: Product, ProductVariant, Category, ProductCategory, and
 * ProductImage - all Store-scoped per ADR 006. This module is closed and exposes no
 * cross-module Java API in this slice; the root package intentionally holds only this
 * module declaration. Implementation types (entities, repositories, application
 * services) live under {@code catalog.internal}, and the HTTP surface lives under
 * {@code catalog.web}. Catalog depends on {@code tenancy} only through its public
 * {@code TenancyAuthorization}/{@code StoreDirectory} API - never {@code
 * tenancy.internal}, tenancy's JPA entities, or tenancy's repositories.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Catalog")
package io.github.abdelrahmankhalil.aicommerce.catalog;
