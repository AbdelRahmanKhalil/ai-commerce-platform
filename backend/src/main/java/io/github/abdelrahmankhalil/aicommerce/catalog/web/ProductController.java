package io.github.abdelrahmankhalil.aicommerce.catalog.web;

import io.github.abdelrahmankhalil.aicommerce.catalog.internal.ImageInput;
import io.github.abdelrahmankhalil.aicommerce.catalog.internal.Product;
import io.github.abdelrahmankhalil.aicommerce.catalog.internal.ProductDetail;
import io.github.abdelrahmankhalil.aicommerce.catalog.internal.ProductImage;
import io.github.abdelrahmankhalil.aicommerce.catalog.internal.ProductService;
import io.github.abdelrahmankhalil.aicommerce.catalog.internal.ProductStatus;
import io.github.abdelrahmankhalil.aicommerce.catalog.internal.ProductVariant;
import io.github.abdelrahmankhalil.aicommerce.catalog.internal.VariantInput;
import io.github.abdelrahmankhalil.aicommerce.catalog.internal.VariantStatus;
import io.github.abdelrahmankhalil.aicommerce.tenancy.TenancyAuthorization;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations/{organizationId}/stores/{storeId}/products")
class ProductController {

    private static final String SLUG_PATTERN = "^[a-z0-9]([a-z0-9-]{0,198}[a-z0-9])?$";

    private final ProductService productService;
    private final TenancyAuthorization tenancyAuthorization;

    ProductController(ProductService productService, TenancyAuthorization tenancyAuthorization) {
        this.productService = productService;
        this.tenancyAuthorization = tenancyAuthorization;
    }

    @PostMapping
    ResponseEntity<CreateProductResponse> create(@PathVariable UUID organizationId,
                                                   @PathVariable UUID storeId,
                                                   @AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody CreateProductRequest request) {
        UUID userAccountId = tenancyAuthorization.resolveUserAccountId(jwt.getSubject());

        List<VariantInput> variants = request.variants().stream()
                .map(v -> new VariantInput(v.sku(), v.status(), v.price(), v.attributes()))
                .toList();
        List<ImageInput> images = orEmpty(request.images()).stream()
                .map(i -> new ImageInput(i.url(), i.altText()))
                .toList();

        UUID productId = productService.createProduct(organizationId, storeId, userAccountId, request.title(),
                request.description(), request.slug(), request.status(), orEmpty(request.categoryIds()), variants, images);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateProductResponse(productId));
    }

    @GetMapping("/{productId}")
    ResponseEntity<ProductDetailResponse> getProduct(@PathVariable UUID organizationId,
                                                        @PathVariable UUID storeId,
                                                        @PathVariable UUID productId,
                                                        @AuthenticationPrincipal Jwt jwt) {
        UUID userAccountId = tenancyAuthorization.resolveUserAccountId(jwt.getSubject());
        ProductDetail detail = productService.getProductDetail(organizationId, storeId, userAccountId, productId);
        return ResponseEntity.ok(toDetailResponse(detail));
    }

    @GetMapping
    ResponseEntity<PageResponse<ProductSummaryResponse>> list(@PathVariable UUID organizationId,
                                                                 @PathVariable UUID storeId,
                                                                 @AuthenticationPrincipal Jwt jwt,
                                                                 @RequestParam(required = false) ProductStatus status,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        UUID userAccountId = tenancyAuthorization.resolveUserAccountId(jwt.getSubject());
        Page<Product> result = productService.listProducts(organizationId, storeId, userAccountId, status, page, size);
        return ResponseEntity.ok(PageResponse.of(result, ProductController::toSummaryResponse));
    }

    @PutMapping("/{productId}")
    ResponseEntity<Void> update(@PathVariable UUID organizationId,
                                 @PathVariable UUID storeId,
                                 @PathVariable UUID productId,
                                 @AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody UpdateProductRequest request) {
        UUID userAccountId = tenancyAuthorization.resolveUserAccountId(jwt.getSubject());
        List<ImageInput> images = orEmpty(request.images()).stream()
                .map(i -> new ImageInput(i.url(), i.altText()))
                .toList();
        productService.updateProduct(organizationId, storeId, productId, userAccountId, request.title(),
                request.description(), request.slug(), request.status(), orEmpty(request.categoryIds()), images);
        return ResponseEntity.ok().build();
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static ProductSummaryResponse toSummaryResponse(Product product) {
        return new ProductSummaryResponse(product.getId(), product.getTitle(), product.getSlug(), product.getStatus(),
                product.getCreatedAt(), product.getUpdatedAt());
    }

    private static ProductDetailResponse toDetailResponse(ProductDetail detail) {
        Product product = detail.product();
        List<VariantResponse> variants = detail.variants().stream()
                .map(ProductController::toVariantResponse)
                .toList();
        List<ImageResponse> images = detail.images().stream()
                .map(ProductController::toImageResponse)
                .toList();
        return new ProductDetailResponse(product.getId(), product.getTitle(), product.getDescription(),
                product.getSlug(), product.getStatus(), product.getCreatedAt(), product.getUpdatedAt(),
                detail.categoryIds(), variants, images);
    }

    private static VariantResponse toVariantResponse(ProductVariant variant) {
        return new VariantResponse(variant.getId(), variant.getSku(), variant.getStatus(), variant.getPrice(),
                variant.getAttributes(), variant.getCreatedAt(), variant.getUpdatedAt());
    }

    private static ImageResponse toImageResponse(ProductImage image) {
        return new ImageResponse(image.getId(), image.getUrl(), image.getAltText(), image.getPosition());
    }

    record CreateProductRequest(
            @NotBlank @Size(max = 500) String title,
            String description,
            @NotBlank @Size(max = 200) @Pattern(regexp = SLUG_PATTERN,
                    message = "must be lowercase letters, digits and hyphens, no leading/trailing hyphen") String slug,
            @NotNull ProductStatus status,
            List<UUID> categoryIds,
            @NotEmpty @Valid List<VariantRequest> variants,
            @Valid List<ImageRequest> images) {
    }

    record UpdateProductRequest(
            @NotBlank @Size(max = 500) String title,
            String description,
            @NotBlank @Size(max = 200) @Pattern(regexp = SLUG_PATTERN,
                    message = "must be lowercase letters, digits and hyphens, no leading/trailing hyphen") String slug,
            @NotNull ProductStatus status,
            List<UUID> categoryIds,
            @Valid List<ImageRequest> images) {
    }

    record VariantRequest(
            @NotBlank @Size(max = 100) String sku,
            @NotNull VariantStatus status,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            Map<String, String> attributes) {
    }

    record ImageRequest(
            @NotBlank String url,
            String altText) {
    }

    record CreateProductResponse(UUID productId) {
    }

    record ProductSummaryResponse(UUID id, String title, String slug, ProductStatus status, Instant createdAt,
                                    Instant updatedAt) {
    }

    record ProductDetailResponse(UUID id, String title, String description, String slug, ProductStatus status,
                                   Instant createdAt, Instant updatedAt, List<UUID> categoryIds,
                                   List<VariantResponse> variants, List<ImageResponse> images) {
    }

    record VariantResponse(UUID id, String sku, VariantStatus status, BigDecimal price, Map<String, String> attributes,
                             Instant createdAt, Instant updatedAt) {
    }

    record ImageResponse(UUID id, String url, String altText, int position) {
    }
}
