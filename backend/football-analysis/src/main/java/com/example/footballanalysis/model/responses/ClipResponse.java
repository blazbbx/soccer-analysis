package com.example.footballanalysis.model.responses;

import com.example.footballanalysis.model.clip.ClipSyncEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ClipResponse(
        UUID id,
        UUID matchId,
        String matchDisplayName,
        String name,
        List<ClipSyncEvent> syncData,
        String renderStatus,
        String renderedStoragePath,
        LocalDateTime createdAt
) {}