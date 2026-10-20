package io.github.abdelrahmankhalil.aicommerce.inventory.internal;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Shared page/size bounds for Inventory's listing endpoint, duplicated locally rather
 * than depending on {@code catalog}'s equivalent helper - small local duplication is
 * preferable to coupling two modules' web/internal packages together just to share a
 * paging helper. There is deliberately no sorting DSL - callers never influence
 * ordering, only page/size.
 */
final class InventoryPaging {

    static final int MAX_SIZE = 100;

    private InventoryPaging() {
    }

    static Pageable of(int page, int size, Sort sort) {
        if (page < 0) {
            throw new InvalidInventoryPagingParametersException("page must be >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidInventoryPagingParametersException("size must be between 1 and " + MAX_SIZE);
        }
        return PageRequest.of(page, size, sort);
    }
}
