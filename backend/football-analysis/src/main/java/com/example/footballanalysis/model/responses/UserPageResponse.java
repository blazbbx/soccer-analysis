package com.example.footballanalysis.model.responses;

import java.util.List;

public record UserPageResponse(
        List<UserResponse> items,
        int page,
        int size,
        long total,
        int totalPages,
        long adminCount
) {
}
