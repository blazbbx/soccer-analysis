package com.example.footballanalysis.model.responses;

import java.util.List;
import java.util.UUID;

/**
 * Ezt küldjük a frontendnek csapat lekérdezéskor.
 * Tartalmazza a játékosok és coachok nevét/id-jét – nincs rekurzió.
 */
public record TeamResponse(
        UUID id,
        String name,
        String shortName,
        String logoUrl,
        List<MemberInfo> players,
        List<MemberInfo> coaches
) {
    /** Egy játékos vagy edző minimális adatai */
    public record MemberInfo(UUID id, String fullName) {}
}

