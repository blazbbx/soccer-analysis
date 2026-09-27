package com.example.footballanalysis.model.responses;

import java.util.UUID;

public record ChildTeamResponse(
        UUID teamId,
        String teamName,
        UUID clubId,
        String clubName
) {}
