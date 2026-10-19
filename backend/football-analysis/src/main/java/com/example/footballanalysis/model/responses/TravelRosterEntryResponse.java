package com.example.footballanalysis.model.responses;

import com.example.footballanalysis.model.db.AttendanceStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record TravelRosterEntryResponse(
        UUID playerProfileId,
        String firstName,
        String lastName,
        AttendanceStatus status,
        boolean wantsRide,
        LocalDateTime respondedAt,
        String comment,
        UUID offerId,
        boolean parentDrives
) {}
