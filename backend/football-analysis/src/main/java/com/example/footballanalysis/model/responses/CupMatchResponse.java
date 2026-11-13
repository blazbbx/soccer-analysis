package com.example.footballanalysis.model.responses;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code leg}: 1 = first match of the pairing, 2 = return match.
 * {@code realMatchId} is null when no analysed match is linked or the caller cannot open it.
 */
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
        LocalDateTime createdAt,
        int leg
) {
}
