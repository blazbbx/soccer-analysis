package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/** Any past or future time is allowed: tournaments are often entered after the video is uploaded. */
public record CupMatchScheduleRequest(
        @NotNull(message = "{validation.cupmatch.home_team.required}")
        UUID homeTeamId,

        @NotNull(message = "{validation.cupmatch.away_team.required}")
        UUID awayTeamId,

        @NotNull(message = "{validation.cupmatch.scheduled_at.required}")
        LocalDateTime scheduledAt
) {
}
