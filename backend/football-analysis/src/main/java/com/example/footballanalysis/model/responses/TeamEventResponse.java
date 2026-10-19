package com.example.footballanalysis.model.responses;

import com.example.footballanalysis.model.db.TeamEventType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TeamEventResponse(
        UUID id,
        UUID teamId,
        TeamEventType type,
        LocalDateTime startsAt,
        LocalDateTime meetingAt,
        String venue,
        String meetingPoint,
        String notes,
        LocalDateTime rsvpDeadline,
        UUID videoMatchId,
        UUID cupMatchId,
        LocalDateTime updatedAt,
        TravelSummaryResponse summary,
        boolean callerCanOfferRide,
        List<TravelCallerProfileResponse> callerProfiles,
        List<TravelRideResponse> rides,
        List<TravelRosterEntryResponse> roster
) {}
