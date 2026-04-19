package com.example.footballanalysis.model.responses;

import java.time.LocalDateTime;
import java.util.UUID;

public record ClipResponse(
        UUID id,
        UUID matchId,
        String matchDisplayName,
        String name,
        Integer startSeconds,
        Integer endSeconds,
        String storagePath,
        LocalDateTime createdAt
) {}