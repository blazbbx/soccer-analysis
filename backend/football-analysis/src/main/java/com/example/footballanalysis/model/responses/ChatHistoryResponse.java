package com.example.footballanalysis.model.responses;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ChatHistoryResponse(
        List<ChatMessageResponse> messages,
        boolean hasMore,
        LocalDateTime nextBeforeCreatedAt,
        UUID nextBeforeId
) {
}

