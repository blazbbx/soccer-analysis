package com.example.footballanalysis.model.responses;

import com.example.footballanalysis.model.db.Cup;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record CupResponse(
        UUID id,
        String name,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt,
        UserResponse createdBy,
        Set<CupTeamResponse> teams,
        Set<CupMatchResponse> matches
) {
}
