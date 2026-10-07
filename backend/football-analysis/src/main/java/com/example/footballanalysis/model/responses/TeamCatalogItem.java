package com.example.footballanalysis.model.responses;

import java.util.UUID;

/**
 * Csapatkatalógus sora: név, klub és létszám. A keret nincs benne.
 */
public record TeamCatalogItem(
        UUID id,
        String name,
        String shortName,
        String logoUrl,
        UUID clubId,
        String clubName,
        long playerCount,
        long coachCount
) {
}
