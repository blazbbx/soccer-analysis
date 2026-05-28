package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.requests.CreateUserRequest;
import com.example.footballanalysis.model.requests.UpdateUserRequest;
import com.example.footballanalysis.model.requests.RegisterUserRequest;
import com.example.footballanalysis.model.responses.UserResponse;
import com.example.footballanalysis.service.UserRegistrationService;
import com.example.footballanalysis.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRegistrationService userRegistrationService;

    /**
     * Visszaadja a rendszerben található összes felhasználót.
     *
     * @return Felhasználók listája.
     */
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /**
     * Lekérdezi egy adott felhasználó adatait azonosító alapján.
     *
     * @param id A felhasználó egyedi azonosítója (UUID).
     * @return A lekérdezett felhasználó adatai.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUser(id));
    }

    /**
     * Új felhasználó adminisztrátori létrehozása a rendszerben.
     * <p>
     * Elvárt payload body: { "email": "...", "firstName": "...", "lastName": "...", "password": "...", "role": "PLAYER" }
     *
     * @param req A létrehozandó felhasználó adatait tartalmazó kérés.
     * @param jwt A hitelesített felhasználó JWT tokenje.
     * @return A sikeresen létrehozott felhasználó adatai.
     */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody CreateUserRequest req) {
        return ResponseEntity.ok(userService.createUser(req, jwt));
    }

    /**
     * Felhasználó regisztrációjának véglegesítése meghívó token (invite token) használatával.
     *
     * @param req A regisztrációs adatokat és a tokent tartalmazó kérés.
     * @return A sikeresen regisztrált felhasználó adatai (201 Created).
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest req) {
        return ResponseEntity.status(201).body(userRegistrationService.registerWithInvite(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable UUID id,
                                                   @AuthenticationPrincipal Jwt jwt,
                                                   @RequestBody UpdateUserRequest req) {
        return ResponseEntity.ok(userService.updateUser(id, req, jwt));
    }

    /**
     * A bejelentkezett felhasználó saját profiljának frissítése.
     *
     * @param jwt A hitelesített felhasználó JWT tokenje.
     * @param req A módosítandó adatok.
     * @return A frissített felhasználó adatai.
     */
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMyProfile(@AuthenticationPrincipal Jwt jwt, @RequestBody UpdateUserRequest req) {
        return ResponseEntity.ok(userService.updateMyUser(req, jwt));
    }

    /**
     * Felhasználó törlése a rendszerből.
     *
     * @param id A törlendő felhasználó egyedi azonosítója (UUID).
     * @param jwt A hitelesített felhasználó JWT tokenje.
     * @return Nem tartalmaz tartalmat (204 No Content).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        userService.deleteUser(id, jwt);
        return ResponseEntity.noContent().build();
    }
}
