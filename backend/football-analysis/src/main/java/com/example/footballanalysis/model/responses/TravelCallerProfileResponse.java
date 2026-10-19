package com.example.footballanalysis.model.responses;

import com.example.footballanalysis.model.db.AttendanceStatus;

import java.util.UUID;

public record TravelCallerProfileResponse(
        UUID playerProfileId,
        String firstName,
        String lastName,
        AttendanceStatus status
) {}
