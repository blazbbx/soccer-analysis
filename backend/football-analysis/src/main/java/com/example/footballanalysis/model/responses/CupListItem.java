package com.example.footballanalysis.model.responses;

import java.time.LocalDateTime;
import java.util.UUID;

public record CupListItem(
        UUID id,
        String name,
        LocalDateTime createdAt,
        UUID createdById,
        String createdByFirstName,
        String createdByLastName,
        long teamCount,
        long matchCount
) {
}
