package com.example.footballanalysis.dto;

import com.example.footballanalysis.model.Corner;

import java.util.List;

// A standard Java Record. It will automatically serialize into clean JSON.
public record VideoProcessingStartMessage(
        String bucketName,
        String fileName,
        String action,
        String matchId,
        List<Corner> corners,
        String homeTeamColor,
        String awayTeamColor,
        String refereeColor
) {}
