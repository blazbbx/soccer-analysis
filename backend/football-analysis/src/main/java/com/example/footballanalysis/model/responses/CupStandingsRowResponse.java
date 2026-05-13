package com.example.footballanalysis.model.responses;

import java.util.UUID;

public record CupStandingsRowResponse(
        UUID teamId,
        String teamName,
        Integer played,
        Integer wins,
        Integer draws,
        Integer losses,
        Integer goalsFor,
        Integer goalsAgainst,
        Integer goalDifference,
        Integer points
) {
}
