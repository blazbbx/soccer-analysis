package com.example.footballanalysis.model.responses;

import java.time.LocalDate;
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
        UUID clubId,
        String clubName,
        List<MemberInfo> players,
        List<MemberInfo> coaches,
        List<MemberInfo> fans,
        List<MemberInfo> parents
) {
    /**
     * Játékosnál az id a player_profiles azonosító, a linkedUserId a belépett fiók (ha van).
     * Edzőnél, szurkolónál és szülőnél az id a user azonosító.
     */
    public record MemberInfo(
            UUID id,
            String firstName,
            String lastName,
            UUID linkedUserId,
            String email,
            LocalDate dateOfBirth
    ) {
        public MemberInfo(UUID id, String firstName, String lastName) {
            this(id, firstName, lastName, null, null, null);
        }

        public MemberInfo(UUID id, String firstName, String lastName, UUID linkedUserId) {
            this(id, firstName, lastName, linkedUserId, null, null);
        }
    }
}
