package com.example.footballanalysis.model.responses;

import java.time.LocalDateTime;
import java.util.List;

public record ChatHistoryResponse(
        List<ChatMessageResponse> messages,
        boolean hasMore,
        LocalDateTime nextBeforeCreatedAt
) {
}

