package io.github.abdelrahmankhalil.aicommerce.inventory.web;

import io.github.abdelrahmankhalil.aicommerce.inventory.internal.InventoryItem;
import io.github.abdelrahmankhalil.aicommerce.inventory.internal.InventoryLookup;
import io.github.abdelrahmankhalil.aicommerce.inventory.internal.InventoryService;
import io.github.abdelrahmankhalil.aicommerce.inventory.internal.InventoryUpsertResult;
import io.github.abdelrahmankhalil.aicommerce.tenancy.TenancyAuthorization;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations/{organizationId}/stores/{storeId}/inventory")
class InventoryController {

    private final InventoryService inventoryService;
    private final TenancyAuthorization tenancyAuthorization;

    InventoryController(InventoryService inventoryService, TenancyAuthorization tenancyAuthorization) {
        this.inventoryService = inventoryService;
        this.tenancyAuthorization = tenancyAuthorization;
    }

    @GetMapping
    ResponseEntity<PageResponse<InventorySummaryResponse>> list(@PathVariable UUID organizationId,
                                                                   @PathVariable UUID storeId,
                                                                   @AuthenticationPrincipal Jwt jwt,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size) {
        UUID userAccountId = tenancyAuthorization.resolveUserAccountId(jwt.getSubject());
        Page<InventoryItem> result = inventoryService.listInventory(organizationId, storeId, userAccountId, page, size);
        return ResponseEntity.ok(PageResponse.of(result, InventoryController::toSummaryResponse));
    }

    @GetMapping("/{variantId}")
    ResponseEntity<InventoryResponse> get(@PathVariable UUID organizationId,
                                            @PathVariable UUID storeId,
                                            @PathVariable UUID variantId,
                                            @AuthenticationPrincipal Jwt jwt) {
        UUID userAccountId = tenancyAuthorization.resolveUserAccountId(jwt.getSubject());
        InventoryLookup lookup = inventoryService.getInventory(organizationId, storeId, userAccountId, variantId);
        return ResponseEntity.ok(toResponse(lookup));
    }

    @PutMapping("/{variantId}")
    ResponseEntity<InventoryResponse> put(@PathVariable UUID organizationId,
                                            @PathVariable UUID storeId,
                                            @PathVariable UUID variantId,
                                            @AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody SetQuantityRequest request) {
        UUID userAccountId = tenancyAuthorization.resolveUserAccountId(jwt.getSubject());
        InventoryUpsertResult result = inventoryService.setOnHandQuantity(organizationId, storeId, userAccountId,
                variantId, request.onHandQuantity());

        InventoryResponse body = new InventoryResponse(result.variantId(), true, result.onHandQuantity(),
                result.createdAt(), result.updatedAt());
        if (result.created()) {
            URI location = URI.create(
                    "/api/organizations/" + organizationId + "/stores/" + storeId + "/inventory/" + variantId);
            return ResponseEntity.created(location).body(body);
        }
        return ResponseEntity.ok(body);
    }

    private static InventoryResponse toResponse(InventoryLookup lookup) {
        return new InventoryResponse(lookup.variantId(), lookup.initialized(), lookup.onHandQuantity(),
                lookup.createdAt(), lookup.updatedAt());
    }

    private static InventorySummaryResponse toSummaryResponse(InventoryItem item) {
        return new InventorySummaryResponse(item.getProductVariantId(), item.getOnHandQuantity(), item.getCreatedAt(),
                item.getUpdatedAt());
    }

    record SetQuantityRequest(@NotNull @Min(0) Long onHandQuantity) {
    }

    record InventoryResponse(UUID variantId, boolean initialized, long onHandQuantity, Instant createdAt,
                               Instant updatedAt) {
    }

    record InventorySummaryResponse(UUID variantId, long onHandQuantity, Instant createdAt, Instant updatedAt) {
    }
}
