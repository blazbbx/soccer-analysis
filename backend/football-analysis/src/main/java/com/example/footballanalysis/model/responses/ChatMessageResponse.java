package com.example.footballanalysis.model.responses;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID teamId,
        UUID senderId,
        String senderName,
        String senderFirstName,
        String senderLastName,
        String content,
        LocalDateTime createdAt,
        List<ChatChildName> senderChildNames,
        String senderRole
) {
}
