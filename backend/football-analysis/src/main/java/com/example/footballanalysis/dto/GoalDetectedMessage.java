package com.example.footballanalysis.dto;

import com.example.footballanalysis.model.Goal;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Result of the Python goal-detection worker ({@code goal.detected} on {@code detection-exchange}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoalDetectedMessage(
        String matchId,
        String status,
        // At most two goals, left to right; empty when the model found none.
        List<Goal> goals,
        String errorMessage
) {}
