package com.example.footballanalysis.model.responses;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code ownTeam}: the linked real team is one of the caller's teams in the active view (highlighted on the UI).
 */
public record CupTeamResponse(
        UUID id,
        String name,
        TeamResponse realTeam,
        LocalDateTime createdAt,
        boolean ownTeam
) {
}
