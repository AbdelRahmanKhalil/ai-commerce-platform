package io.github.abdelrahmankhalil.aicommerce.inventory.internal;

import io.github.abdelrahmankhalil.aicommerce.catalog.CatalogVariantDirectory;
import io.github.abdelrahmankhalil.aicommerce.tenancy.TenancyAuthorization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Single responsibility: Inventory's read/write operations (ADR 007). Every
 * operation authorizes Store access first, before any Catalog lookup or Inventory
 * read/write - a cross-tenant caller must never learn whether a Variant or
 * InventoryItem exists. A missing InventoryItem for an otherwise-valid Variant is a
 * valid "uninitialized" read result, never an error; {@code setOnHandQuantity} is the
 * sole write path, an absolute-replacement upsert arbitrated by JPA optimistic
 * locking ({@code @Version}) for an already-existing row, and by the database's
 * {@code UNIQUE (product_variant_id)} constraint for concurrent first
 * initialization.
 */
@Service
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final TenancyAuthorization tenancyAuthorization;
    private final CatalogVariantDirectory catalogVariantDirectory;

    InventoryService(InventoryItemRepository inventoryItemRepository, TenancyAuthorization tenancyAuthorization,
                      CatalogVariantDirectory catalogVariantDirectory) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.tenancyAuthorization = tenancyAuthorization;
        this.catalogVariantDirectory = catalogVariantDirectory;
    }

    @Transactional(readOnly = true)
    public InventoryLookup getInventory(UUID organizationId, UUID storeId, UUID userAccountId, UUID variantId) {
        tenancyAuthorization.requireStoreAccess(userAccountId, organizationId, storeId, InventoryRoles.READ);
        requireVariant(organizationId, storeId, variantId);

        return inventoryItemRepository
                .findByProductVariantIdAndStoreIdAndOrganizationId(variantId, storeId, organizationId)
                .map(InventoryLookup::initialized)
                .orElseGet(() -> InventoryLookup.uninitialized(variantId));
    }

    @Transactional(readOnly = true)
    public Page<InventoryItem> listInventory(UUID organizationId, UUID storeId, UUID userAccountId, int page,
                                               int size) {
        tenancyAuthorization.requireStoreAccess(userAccountId, organizationId, storeId, InventoryRoles.READ);
        Pageable pageable = InventoryPaging.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return inventoryItemRepository.findByStoreIdAndOrganizationId(storeId, organizationId, pageable);
    }

    /**
     * Absolute-replacement upsert: creates the InventoryItem on its first write for a
     * Variant, or replaces the quantity of an already-existing one. Concurrency for
     * the existing-row case is guaranteed by {@code @Version} (surfaces as {@code
     * OptimisticLockingFailureException} at flush/commit time, mapped to HTTP 409 by
     * {@code InventoryExceptionHandler}); concurrency for first initialization is
     * arbitrated by the database's {@code UNIQUE (product_variant_id)} constraint
     * (surfaces as {@code DataIntegrityViolationException}, mapped to HTTP 409 by the
     * shared {@code GlobalExceptionHandler}) - never a distributed lock.
     */
    @Transactional
    public InventoryUpsertResult setOnHandQuantity(UUID organizationId, UUID storeId, UUID userAccountId,
                                                     UUID variantId, long onHandQuantity) {
        tenancyAuthorization.requireStoreAccess(userAccountId, organizationId, storeId, InventoryRoles.WRITE);
        if (onHandQuantity < 0) {
            throw new InvalidInventoryQuantityException("onHandQuantity must be >= 0");
        }
        requireVariant(organizationId, storeId, variantId);

        Optional<InventoryItem> existing = inventoryItemRepository
                .findByProductVariantIdAndStoreIdAndOrganizationId(variantId, storeId, organizationId);
        if (existing.isPresent()) {
            InventoryItem item = existing.get();
            item.replaceOnHandQuantity(onHandQuantity);
            return InventoryUpsertResult.updated(item);
        }

        InventoryItem created = inventoryItemRepository.save(
                new InventoryItem(organizationId, storeId, variantId, onHandQuantity));
        return InventoryUpsertResult.created(created);
    }

    private void requireVariant(UUID organizationId, UUID storeId, UUID variantId) {
        catalogVariantDirectory.findVariant(organizationId, storeId, variantId)
                .orElseThrow(() -> new InventoryVariantNotFoundException(variantId));
    }
}
