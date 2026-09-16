package io.github.abdelrahmankhalil.aicommerce.catalog.web;

import io.github.abdelrahmankhalil.aicommerce.catalog.internal.Category;
import io.github.abdelrahmankhalil.aicommerce.catalog.internal.CategoryService;
import io.github.abdelrahmankhalil.aicommerce.tenancy.TenancyAuthorization;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations/{organizationId}/stores/{storeId}/categories")
class CategoryController {

    private static final String SLUG_PATTERN = "^[a-z0-9]([a-z0-9-]{0,198}[a-z0-9])?$";

    private final CategoryService categoryService;
    private final TenancyAuthorization tenancyAuthorization;

    CategoryController(CategoryService categoryService, TenancyAuthorization tenancyAuthorization) {
        this.categoryService = categoryService;
        this.tenancyAuthorization = tenancyAuthorization;
    }

    @PostMapping
    ResponseEntity<CreateCategoryResponse> create(@PathVariable UUID organizationId,
                                                    @PathVariable UUID storeId,
                                                    @AuthenticationPrincipal Jwt jwt,
                                                    @Valid @RequestBody CreateCategoryRequest request) {
        UUID userAccountId = tenancyAuthorization.resolveUserAccountId(jwt.getSubject());
        UUID categoryId = categoryService.create(organizationId, storeId, userAccountId, request.name(), request.slug());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateCategoryResponse(categoryId));
    }

    @GetMapping
    ResponseEntity<PageResponse<CategoryResponse>> list(@PathVariable UUID organizationId,
                                                          @PathVariable UUID storeId,
                                                          @AuthenticationPrincipal Jwt jwt,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        UUID userAccountId = tenancyAuthorization.resolveUserAccountId(jwt.getSubject());
        Page<Category> result = categoryService.list(organizationId, storeId, userAccountId, page, size);
        return ResponseEntity.ok(PageResponse.of(result, CategoryController::toResponse));
    }

    @PutMapping("/{categoryId}")
    ResponseEntity<Void> update(@PathVariable UUID organizationId,
                                 @PathVariable UUID storeId,
                                 @PathVariable UUID categoryId,
                                 @AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody UpdateCategoryRequest request) {
        UUID userAccountId = tenancyAuthorization.resolveUserAccountId(jwt.getSubject());
        categoryService.update(organizationId, storeId, categoryId, userAccountId, request.name(), request.slug());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{categoryId}")
    ResponseEntity<Void> delete(@PathVariable UUID organizationId,
                                 @PathVariable UUID storeId,
                                 @PathVariable UUID categoryId,
                                 @AuthenticationPrincipal Jwt jwt) {
        UUID userAccountId = tenancyAuthorization.resolveUserAccountId(jwt.getSubject());
        categoryService.delete(organizationId, storeId, categoryId, userAccountId);
        return ResponseEntity.noContent().build();
    }

    private static CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSlug(), category.getCreatedAt(),
                category.getUpdatedAt());
    }

    record CreateCategoryRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 200) @Pattern(regexp = SLUG_PATTERN,
                    message = "must be lowercase letters, digits and hyphens, no leading/trailing hyphen") String slug) {
    }

    record UpdateCategoryRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 200) @Pattern(regexp = SLUG_PATTERN,
                    message = "must be lowercase letters, digits and hyphens, no leading/trailing hyphen") String slug) {
    }

    record CreateCategoryResponse(UUID categoryId) {
    }

    record CategoryResponse(UUID id, String name, String slug, Instant createdAt, Instant updatedAt) {
    }
}
