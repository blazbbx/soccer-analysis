package com.example.footballanalysis.dto;

/** Request to the goal-detection worker: it streams the raw video from {@code videoUrl}. */
public record GoalDetectionStartMessage(
        String matchId,
        String videoUrl
) {}
