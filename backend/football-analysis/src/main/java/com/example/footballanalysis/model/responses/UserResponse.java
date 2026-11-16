package com.example.footballanalysis.model.responses;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Ezt küldjük a frontendnek user lekérdezéskor.
 * A password sosem kerül bele!
 * teams: csak Player és Coach esetén van kitöltve, Admin/Fan esetén null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        LocalDateTime createdAt,
        List<TeamInfo> teams,
        /** Platform admin in Keycloak. Only the user lists fill it; elsewhere it is left out. */
        Boolean platformAdmin,
        /** Admin of the club whose user list this is. Only the club user list fills it. */
        Boolean clubAdmin) {

    public UserResponse(UUID id, String email, String firstName, String lastName,
                        LocalDateTime createdAt, List<TeamInfo> teams) {
        this(id, email, firstName, lastName, createdAt, teams, null, null);
    }

    /** Egy csapat minimális adatai – csak Player/Coach válaszban jelenik meg */
    public record TeamInfo(UUID id, String name) {}
}
