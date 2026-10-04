package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

import java.util.UUID;

/**
 * No Product with the given id exists within the requested Store/Organization -
 * either it never existed, or it belongs to a different Store/Organization. Maps to
 * HTTP 404.
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(UUID productId) {
        super("Product " + productId + " not found");
    }
}
