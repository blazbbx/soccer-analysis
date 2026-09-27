package com.example.footballanalysis.model.requests;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Extra adatok egy meghívó elfogadásához.
 * Nyílt szülői meghívón a gyerek neve és születési dátuma, vagy egy meglévő profil.
 * Felnőtt játékos-meghívón a belépő saját születési dátuma.
 */
public record AcceptInviteRequest(
        String childFirstName,
        String childLastName,
        LocalDate childDateOfBirth,
        LocalDate dateOfBirth,
        UUID existingProfileId
) {
    public static AcceptInviteRequest empty() {
        return new AcceptInviteRequest(null, null, null, null, null);
    }
}
