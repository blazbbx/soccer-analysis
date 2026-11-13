package com.example.footballanalysis.model.responses;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A cup with its teams (in the order they were added) and matches (by time).
 * {@code canManage}: the caller may edit this cup in the active view. The edit buttons depend on it.
 */
public record CupResponse(
        UUID id,
        String name,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt,
        UserResponse createdBy,
        List<CupTeamResponse> teams,
        List<CupMatchResponse> matches,
        boolean canManage
) {
}
