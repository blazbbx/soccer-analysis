package com.example.footballanalysis.model.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.example.footballanalysis.model.db.user.UserRole;

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
        String fullName,
        UserRole role,
        LocalDateTime createdAt,
        List<TeamInfo> teams) {

    /** Egy csapat minimális adatai – csak Player/Coach válaszban jelenik meg */
    public record TeamInfo(UUID id, String name) {}
}

