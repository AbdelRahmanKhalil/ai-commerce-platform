/**
 * Catalog module: Product, ProductVariant, Category, ProductCategory, and
 * ProductImage - all Store-scoped per ADR 006. This module is closed. Its root
 * package now exposes one intentionally small cross-module Java API,
 * {@link io.github.abdelrahmankhalil.aicommerce.catalog.CatalogVariantDirectory},
 * added per ADR 007 once Inventory became catalog's first concrete cross-module
 * consumer - scoped ProductVariant existence/identity only, never the
 * {@code ProductVariant} JPA entity, repositories, Product data, SKU, price,
 * attributes, categories, images, or Variant status. Implementation types (entities,
 * repositories, application services) live under {@code catalog.internal}, and the
 * HTTP surface lives under {@code catalog.web}. Catalog depends on {@code tenancy}
 * only through its public {@code TenancyAuthorization}/{@code StoreDirectory} API -
 * never {@code tenancy.internal}, tenancy's JPA entities, or tenancy's repositories.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Catalog")
package io.github.abdelrahmankhalil.aicommerce.catalog;
