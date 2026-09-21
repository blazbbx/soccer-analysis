package com.example.footballanalysis.model.responses;

import java.time.Instant;
import java.util.UUID;

public record ClubResponse(
        UUID id,
        String name,
        String shortName,
        String logoUrl,
        Instant createdAt
) {
}
