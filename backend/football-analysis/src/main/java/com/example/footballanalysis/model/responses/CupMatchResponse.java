package com.example.footballanalysis.model.responses;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.UUID;

public record CupMatchResponse(
        UUID id,
        CupTeamResponse homeTeam,
        CupTeamResponse awayTeam,
        Integer homeScore,
        Integer awayScore,
        boolean played,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime scheduledAt,
        UUID realMatchId,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt
) {
}
