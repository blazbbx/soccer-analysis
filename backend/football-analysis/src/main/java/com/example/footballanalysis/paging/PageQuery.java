package com.example.footballanalysis.paging;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.model.responses.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

public final class PageQuery {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    private PageQuery() {
    }

    public static int page(Integer page) {
        int value = page == null ? 1 : page;
        if (value < 1) {
            throw new BadRequestException(
                    "error.validation.invalid_request",
                    new Object[]{"page"},
                    "Invalid request: page");
        }
        return value;
    }

    public static int size(Integer size) {
        int value = size == null ? DEFAULT_SIZE : size;
        if (value < 1 || value > MAX_SIZE) {
            throw new BadRequestException(
                    "error.validation.invalid_request",
                    new Object[]{"size"},
                    "Invalid request: size");
        }
        return value;
    }

    public static Pageable of(int page, int size) {
        return PageRequest.of(page - 1, size);
    }

    public static int totalPages(long total, int size) {
        if (total <= 0 || size <= 0) {
            return 0;
        }
        return (int) ((total + size - 1) / size);
    }

    public static <T> PageResponse<T> envelope(List<T> items, int page, int size, long total) {
        return new PageResponse<>(items, page, size, total, totalPages(total, size));
    }
}
