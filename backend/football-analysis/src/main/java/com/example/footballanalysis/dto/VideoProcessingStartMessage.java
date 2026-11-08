package com.example.footballanalysis.dto;

import com.example.footballanalysis.model.Corner;
import com.example.footballanalysis.model.Goal;

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
        String refereeColor,
        // The goals the user confirmed (0-2). The ML worker does not read this yet: it sits in the
        // contract so a consumer (e.g. the event detector) can use the real goal position.
        List<Goal> goals
) {}
