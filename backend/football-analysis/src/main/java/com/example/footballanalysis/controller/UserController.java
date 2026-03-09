package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.requests.CreateUserRequest;
import com.example.footballanalysis.model.responses.UserResponse;
import com.example.footballanalysis.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** GET /api/users – összes user listázása */
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /** GET /api/users/{id} – egy user lekérdezése */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(userService.getUser(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * POST /api/users – új user létrehozása
     * Body: { "username": "...", "email": "...", "fullName": "...", "password": "...", "role": "PLAYER" }
     */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody CreateUserRequest req) {
        try {
            return ResponseEntity.ok(userService.createUser(req));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /** DELETE /api/users/{id} – user törlése */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
