package com.farmcrop.common.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Stable pagination payload, independent of the Spring Data Page JSON format. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.map(mapper).getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
