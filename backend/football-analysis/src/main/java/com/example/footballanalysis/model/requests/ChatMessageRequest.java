package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A chat message. Without clipId the content is required (checked in ChatService);
 * with clipId it is an optional comment and may be empty.
 */
public record ChatMessageRequest(
        @Size(max = 5000, message = "{validation.message.content.max}")
        String content,
        UUID clipId
) {
}
