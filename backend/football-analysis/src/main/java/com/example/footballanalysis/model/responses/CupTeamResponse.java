package com.example.footballanalysis.model.responses;

import java.time.LocalDateTime;
import java.util.UUID;

public record CupTeamResponse(
        UUID id,
        String name,
        TeamResponse realTeam,
        LocalDateTime createdAt
) {
}
