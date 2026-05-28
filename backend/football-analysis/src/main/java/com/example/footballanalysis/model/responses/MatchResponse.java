package com.example.footballanalysis.model.responses;

import com.example.footballanalysis.model.Corner;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record MatchResponse(
        UUID id,
        // Csapatok
        UUID homeTeamId,
        String homeTeamName,
        UUID awayTeamId,
        String awayTeamName,
        // Meccs metaadatok
        String homeTeamColor,
        String awayTeamColor,
        String refereeColor,
        LocalDateTime matchDate,
        Integer homeScore,
        Integer awayScore,
        // Videó
        String originalFileName,
        String hlsManifestUrl,
        String trackingDataUrl,
        // Field detection
        String defishedImageUrl,
        List<Corner> fieldCorners,
        String fieldDetectionStatus,
        // Státuszok
        String overallStatus,
        String mlStatus,
        String encodingStatus,
        LocalDateTime createdAt
) {}
