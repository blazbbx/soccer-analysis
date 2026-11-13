package com.example.footballanalysis.model.responses;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row of the cup list. The creator fields are null when the creator's account was deleted.
 * {@code canManage} is filled by the service for the caller's active view (same rule as the write endpoints).
 */
public record CupListItem(
        UUID id,
        String name,
        LocalDateTime createdAt,
        UUID createdById,
        String createdByFirstName,
        String createdByLastName,
        long teamCount,
        long matchCount,
        boolean canManage
) {
    /** Used by the JPQL constructor expressions in {@code CupRepository}. */
    public CupListItem(UUID id, String name, LocalDateTime createdAt, UUID createdById,
                       String createdByFirstName, String createdByLastName, long teamCount, long matchCount) {
        this(id, name, createdAt, createdById, createdByFirstName, createdByLastName, teamCount, matchCount, false);
    }

    public CupListItem withCanManage(boolean value) {
        return new CupListItem(id, name, createdAt, createdById, createdByFirstName, createdByLastName,
                teamCount, matchCount, value);
    }
}
