package io.github.abdelrahmankhalil.aicommerce.catalog.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * A minimal, explicit paginated response shape for catalog's listing endpoints -
 * deliberately not Spring Data's {@code Page}/{@code PagedModel} directly, to avoid
 * leaking JPA/Spring Data types into the HTTP contract.
 */
record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
