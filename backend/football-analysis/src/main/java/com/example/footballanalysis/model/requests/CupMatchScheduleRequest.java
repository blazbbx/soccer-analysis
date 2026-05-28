package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record CupMatchScheduleRequest(
        @NotNull(message = "{validation.cupmatch.home_team.required}")
        UUID homeTeamId,

        @NotNull(message = "{validation.cupmatch.away_team.required}")
        UUID awayTeamId,

        @NotNull(message = "{validation.cupmatch.scheduled_at.required}")
        @FutureOrPresent(message = "{validation.cupmatch.scheduled_at.future}")
        LocalDateTime scheduledAt
) {
}
