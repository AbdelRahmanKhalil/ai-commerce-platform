package io.github.abdelrahmankhalil.aicommerce.catalog.web;

import io.github.abdelrahmankhalil.aicommerce.catalog.internal.ProductVariantService;
import io.github.abdelrahmankhalil.aicommerce.catalog.internal.VariantStatus;
import io.github.abdelrahmankhalil.aicommerce.tenancy.TenancyAuthorization;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations/{organizationId}/stores/{storeId}/products/{productId}/variants")
class ProductVariantController {

    private final ProductVariantService variantService;
    private final TenancyAuthorization tenancyAuthorization;

    ProductVariantController(ProductVariantService variantService, TenancyAuthorization tenancyAuthorization) {
        this.variantService = variantService;
        this.tenancyAuthorization = tenancyAuthorization;
    }

    @PostMapping
    ResponseEntity<CreateVariantResponse> create(@PathVariable UUID organizationId,
                                                   @PathVariable UUID storeId,
                                                   @PathVariable UUID productId,
                                                   @AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody CreateVariantRequest request) {
        UUID userAccountId = tenancyAuthorization.resolveUserAccountId(jwt.getSubject());
        UUID variantId = variantService.addVariant(organizationId, storeId, productId, userAccountId, request.sku(),
                request.status(), request.price(), request.attributes());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateVariantResponse(variantId));
    }

    @PutMapping("/{variantId}")
    ResponseEntity<Void> update(@PathVariable UUID organizationId,
                                 @PathVariable UUID storeId,
                                 @PathVariable UUID productId,
                                 @PathVariable UUID variantId,
                                 @AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody UpdateVariantRequest request) {
        UUID userAccountId = tenancyAuthorization.resolveUserAccountId(jwt.getSubject());
        variantService.updateVariant(organizationId, storeId, productId, variantId, userAccountId, request.sku(),
                request.price(), request.attributes(), request.status());
        return ResponseEntity.ok().build();
    }

    record CreateVariantRequest(
            @NotBlank @Size(max = 100) String sku,
            @NotNull VariantStatus status,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            Map<String, String> attributes) {
    }

    record UpdateVariantRequest(
            @NotBlank @Size(max = 100) String sku,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            Map<String, String> attributes,
            @NotNull VariantStatus status) {
    }

    record CreateVariantResponse(UUID variantId) {
    }
}
