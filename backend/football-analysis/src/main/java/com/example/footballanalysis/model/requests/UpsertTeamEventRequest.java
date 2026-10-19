package com.example.footballanalysis.model.requests;

import com.example.footballanalysis.model.db.TeamEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record UpsertTeamEventRequest(
        @NotNull TeamEventType type,
        @NotNull LocalDateTime startsAt,
        @NotNull(message = "{validation.travel.meeting_at}") LocalDateTime meetingAt,
        @Size(max = 255) String venue,
        @NotBlank(message = "{validation.travel.meeting_point}") @Size(max = 255) String meetingPoint,
        @Size(max = 2000) String notes,
        LocalDateTime rsvpDeadline,
        UUID videoMatchId,
        UUID cupMatchId,
        LocalDateTime expectedUpdatedAt
) {}
