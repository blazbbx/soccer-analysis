package com.example.footballanalysis.model.requests;

/**
 * Felhasználó létrehozásához szükséges adatok.
 * role: ADMIN | PLAYER | COACH | FAN
 */
public record CreateUserRequest(
        String email,
        String fullName,
        String password,   // nyers jelszó – a service hash-eli
        String role        // "ADMIN", "PLAYER", "COACH", "FAN"
) {}

