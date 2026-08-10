package com.deploytrack.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

public record PagedResponse<T>(List<T> content, int page, int size, long totalElements) {

    public static <E, T> PagedResponse<T> from(Page<E> springPage, Function<E, T> mapper) {
        return new PagedResponse<>(
            springPage.getContent().stream().map(mapper).toList(),
            springPage.getNumber(),
            springPage.getSize(),
            springPage.getTotalElements()
        );
    }
}
