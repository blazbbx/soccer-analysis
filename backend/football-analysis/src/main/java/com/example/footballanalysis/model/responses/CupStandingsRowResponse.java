package com.example.footballanalysis.model.responses;

import java.util.UUID;

/**
 * One row of the cup table. The list is already in final order; the position is the row index + 1.
 * {@code ownTeam}: the row's real team is one of the caller's teams in the active view.
 */
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
        Integer points,
        boolean ownTeam
) {
}
