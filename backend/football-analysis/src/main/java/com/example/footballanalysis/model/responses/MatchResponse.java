package com.example.footballanalysis.model.responses;

import java.time.LocalDateTime;
import java.util.UUID;

public record MatchResponse(
        UUID id,
        UUID homeTeamId,
        UUID awayTeamId,
        String originalFileName,
        String overallStatus,
        String mlStatus,
        String encodingStatus,
        String hlsManifestUrl,
        String trackingDataUrl,
        LocalDateTime createdAt
) {}