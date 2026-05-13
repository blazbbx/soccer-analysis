package com.example.footballanalysis.model.responses;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID teamId,
        UUID senderId,
        String senderName,
        String content,
        LocalDateTime createdAt
) {
}
