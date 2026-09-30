package com.example.footballanalysis.model.responses;

import java.time.Instant;
import java.util.UUID;

public record ClubAdminResponse(
        UUID userId,
        String firstName,
        String lastName,
        String email,
        Instant createdAt
) {
}
