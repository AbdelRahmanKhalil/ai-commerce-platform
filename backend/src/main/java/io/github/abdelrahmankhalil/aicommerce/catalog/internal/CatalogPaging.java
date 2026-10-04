package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Shared page/size bounds for catalog's listing endpoints (Category and Product).
 * There is deliberately no sorting DSL - callers never influence ordering, only
 * page/size.
 */
final class CatalogPaging {

    static final int MAX_SIZE = 100;

    private CatalogPaging() {
    }

    static Pageable of(int page, int size, Sort sort) {
        if (page < 0) {
            throw new InvalidPagingParametersException("page must be >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidPagingParametersException("size must be between 1 and " + MAX_SIZE);
        }
        return PageRequest.of(page, size, sort);
    }
}
