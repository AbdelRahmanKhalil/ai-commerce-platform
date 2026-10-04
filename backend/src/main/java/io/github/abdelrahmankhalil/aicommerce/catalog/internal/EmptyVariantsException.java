package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

/**
 * A Product create request supplied no initial ProductVariant. ADR 006 makes "every
 * Product has at least one ProductVariant" an application invariant enforced at the
 * Catalog write transaction boundary, by {@code ProductService} itself - this check
 * must not depend on the controller's {@code @NotEmpty} Bean Validation, since
 * {@code ProductService.createProduct} is also callable directly. Maps to HTTP 400.
 */
public class EmptyVariantsException extends RuntimeException {

    public EmptyVariantsException() {
        super("A Product must be created with at least one Variant");
    }
}
