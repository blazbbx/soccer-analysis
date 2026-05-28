package com.example.footballanalysis.model.requests;

import java.time.LocalDateTime;
import java.util.UUID;

public record UpdateMatchRequest(
    UUID homeTeamId,
    UUID awayTeamId,
    LocalDateTime matchDate,
    Integer homeScore,
    Integer awayScore
) {}
