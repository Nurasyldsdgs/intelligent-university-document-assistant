package com.university.docassistant.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable JSON shape for a page of results; {@code page} is zero-based, like the {@code ?page=} parameter.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
}
