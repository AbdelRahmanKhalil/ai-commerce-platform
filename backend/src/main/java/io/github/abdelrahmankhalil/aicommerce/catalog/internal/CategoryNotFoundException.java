package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

import java.util.UUID;

/**
 * No Category with the given id exists within the requested Store/Organization -
 * either it never existed, or it belongs to a different Store/Organization. Both
 * cases are indistinguishable to the caller, so cross-tenant access does not leak
 * resource existence. Maps to HTTP 404.
 */
public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(UUID categoryId) {
        super("Category " + categoryId + " not found");
    }
}
