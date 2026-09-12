package io.github.abdelrahmankhalil.aicommerce.tenancy.web;

import io.github.abdelrahmankhalil.aicommerce.tenancy.StoreDirectory;
import io.github.abdelrahmankhalil.aicommerce.tenancy.StoreRole;
import io.github.abdelrahmankhalil.aicommerce.tenancy.StoreSummary;
import io.github.abdelrahmankhalil.aicommerce.tenancy.TenancyAuthorization;
import io.github.abdelrahmankhalil.aicommerce.tenancy.internal.StoreManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations/{organizationId}/stores")
class StoreController {

    /** A Store-scoped read (e.g. GET store) is allowed by any StoreAccess role. */
    private static final EnumSet<StoreRole> ANY_STORE_ROLE = EnumSet.allOf(StoreRole.class);

    private static final String SLUG_PATTERN = "^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$";
    private static final String CURRENCY_CODE_PATTERN = "^[A-Z]{3}$";

    private final StoreManagementService storeManagementService;
    private final TenancyAuthorization tenancyAuthorization;
    private final StoreDirectory storeDirectory;

    StoreController(StoreManagementService storeManagementService,
                     TenancyAuthorization tenancyAuthorization,
                     StoreDirectory storeDirectory) {
        this.storeManagementService = storeManagementService;
        this.tenancyAuthorization = tenancyAuthorization;
        this.storeDirectory = storeDirectory;
    }

    @PostMapping
    ResponseEntity<CreateStoreResponse> createStore(@PathVariable UUID organizationId,
                                                      @AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody CreateStoreRequest request) {
        UUID userAccountId = tenancyAuthorization.resolveUserAccountId(jwt.getSubject());
        UUID storeId = storeManagementService.createStore(
                organizationId, userAccountId, request.name(), request.slug(), request.currencyCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateStoreResponse(storeId));
    }

    @GetMapping("/{storeId}")
    ResponseEntity<StoreSummary> getStore(@PathVariable UUID organizationId,
                                           @PathVariable UUID storeId,
                                           @AuthenticationPrincipal Jwt jwt) {
        UUID userAccountId = tenancyAuthorization.resolveUserAccountId(jwt.getSubject());
        tenancyAuthorization.requireStoreAccess(userAccountId, organizationId, storeId, ANY_STORE_ROLE);
        StoreSummary summary = storeDirectory.getStore(organizationId, storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found"));
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/{storeId}/store-access")
    ResponseEntity<GrantStoreAccessResponse> grantStoreAccess(@PathVariable UUID organizationId,
                                                                @PathVariable UUID storeId,
                                                                @AuthenticationPrincipal Jwt jwt,
                                                                @Valid @RequestBody GrantStoreAccessRequest request) {
        UUID requestingUserAccountId = tenancyAuthorization.resolveUserAccountId(jwt.getSubject());
        UUID grantId = storeManagementService.grantStoreAccess(
                organizationId, requestingUserAccountId, storeId, request.userAccountId(), request.role());
        return ResponseEntity.status(HttpStatus.CREATED).body(new GrantStoreAccessResponse(grantId));
    }

    record CreateStoreRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 63) @Pattern(regexp = SLUG_PATTERN,
                    message = "must be a valid DNS label: lowercase letters, digits and hyphens, "
                            + "1-63 characters, no leading/trailing hyphen") String slug,
            @NotBlank @Pattern(regexp = CURRENCY_CODE_PATTERN, message = "must be a 3-letter ISO-4217 code") String currencyCode) {
    }

    record CreateStoreResponse(UUID storeId) {
    }

    record GrantStoreAccessRequest(
            @NotNull UUID userAccountId,
            @NotNull StoreRole role) {
    }

    record GrantStoreAccessResponse(UUID storeAccessId) {
    }
}
