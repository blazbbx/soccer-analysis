package com.example.footballanalysis.model.responses;

import java.time.LocalDateTime;
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
        String homeTeamShortsColor,
        String homeTeamSocksColor,
        String awayTeamShortsColor,
        String awayTeamSocksColor,
        LocalDateTime matchDate,
        Integer homeScore,
        Integer awayScore,
        // Videó
        String originalFileName,
        String hlsManifestUrl,
        String trackingDataUrl,
        // Státuszok
        String overallStatus,
        String mlStatus,
        String encodingStatus,
        LocalDateTime createdAt
) {}