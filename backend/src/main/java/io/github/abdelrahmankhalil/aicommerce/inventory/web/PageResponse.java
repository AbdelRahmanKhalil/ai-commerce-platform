package io.github.abdelrahmankhalil.aicommerce.inventory.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * A minimal, explicit paginated response shape for Inventory's listing endpoint -
 * deliberately not Spring Data's {@code Page}/{@code PagedModel} directly, to avoid
 * leaking JPA/Spring Data types into the HTTP contract. Duplicated locally rather
 * than depending on {@code catalog.web}'s equivalent - small local duplication is
 * preferable to coupling two modules' web packages together.
 */
record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
