package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.db.user.*;
import com.example.footballanalysis.model.requests.CreateUserRequest;
import com.example.footballanalysis.model.responses.UserResponse;
import com.example.footballanalysis.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        return toResponse(userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id)));
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest req) {
        if (userRepository.findByEmail(req.email()).isPresent()) {
            throw new RuntimeException("Email already in use: " + req.email());
        }

        User user = switch (req.role().toUpperCase()) {
            case "ADMIN"  -> { var u = new Admin();  u.setRole(UserRole.ADMIN);  yield u; }
            case "PLAYER" -> { var u = new Player(); u.setRole(UserRole.PLAYER); yield u; }
            case "COACH"  -> { var u = new Coach();  u.setRole(UserRole.COACH);  yield u; }
            case "FAN"    -> { var u = new Fan();    u.setRole(UserRole.FAN);    yield u; }
            default -> throw new RuntimeException("Unknown role: " + req.role());
        };

        user.setEmail(req.email());
        user.setFullName(req.fullName());
        user.setPassword(passwordEncoder.encode(req.password()));

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(UUID id) {
        userRepository.deleteById(id);
    }

    // ── Entitás → DTO konverzió ───────────────────────────────────────────────
    private UserResponse toResponse(User user) {
        // Csak Player és Coach esetén küldjük ki a csapatokat
        List<UserResponse.TeamInfo> teams = null;
        if (user instanceof Player p) {
            teams = p.getTeams().stream()
                    .map(t -> new UserResponse.TeamInfo(t.getId(), t.getName()))
                    .toList();
        } else if (user instanceof Coach c) {
            teams = c.getTeams().stream()
                    .map(t -> new UserResponse.TeamInfo(t.getId(), t.getName()))
                    .toList();
        } else if (user instanceof Fan f) {
            teams = f.getTeams().stream()
                    .map(t -> new UserResponse.TeamInfo(t.getId(), t.getName()))
                    .toList();
        }
        // Admin esetén teams = null → nem kerül bele a JSON-ba (ha @JsonInclude(NON_NULL) van)

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getCreatedAt(),
                teams
        );
    }
}
