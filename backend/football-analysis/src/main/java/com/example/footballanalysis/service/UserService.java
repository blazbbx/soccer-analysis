package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.FieldConflictException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.db.user.*;
import com.example.footballanalysis.model.requests.CreateUserRequest;
import com.example.footballanalysis.model.requests.UpdateUserRequest;
import com.example.footballanalysis.model.responses.UserResponse;
import com.example.footballanalysis.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final KeycloakUserAdminService keycloakUserAdminService;
    private final AuditEventService auditEventService;

    /**
     * Visszaadja az összes regisztrált felhasználót a rendszerből.
     *
     * @return a felhasználók listája DTO formátumban
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        log.debug("Fetching all users from database...");
        List<User> usersFromDb = userRepository.findAll();
        if (usersFromDb == null || usersFromDb.isEmpty()) {
            log.debug("Found 0 users");
            return List.of();
        }

        List<UserResponse> users = usersFromDb.stream().map(this::toResponse).toList();
        log.debug("Found {} users", users.size());
        return users;
    }

    /**
     * Lekérdez egy adott felhasználót az egyedi azonosítója (UUID) alapján.
     *
     * @param id a keresett felhasználó egyedi azonosítója
     * @return a megtalált felhasználó adatai DTO formátumban
     * @throws NotFoundException ha a megadott azonosítóval nem található felhasználó
     */
    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        log.debug("Fetching user with ID: {}", id);
        return userRepository.findById(id)
                .map(user -> {
                    log.debug("User found with ID: {}", id);
                    return toResponse(user);
                })
                .orElseThrow(() -> {
                    return new NotFoundException("error.user.not_found", new Object[]{id}, "User not found: " + id);
                });
    }

    /**
     * Új felhasználót hoz létre a rendszerben a megadott adatok alapján.
     * Ellenőrzi, hogy az e-mail cím foglalt-e, beállítja a kért jogosultsági szintet (szerepkört),
     * és elmenti az új entitást az adatbázisba.
     *
     * @param req a létrehozandó felhasználó adatait tartalmazó kérés (DTO)
     * @return a sikeresen létrehozott felhasználó adatai
     * @throws FieldConflictException ha a megadott e-mail cím már létezik a rendszerben
     * @throws BadRequestException ha a megadott szerepkör (role) ismeretlen
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest req, Jwt jwt) {
        log.debug("Attempting to create user with role: {}", req.role());

        UUID actorUserId = resolveCurrentUserId(jwt);

        // Formátum/jelenlét validáció: a @Valid annotáció a Controller rétegben elvégzi.
        // Itt csak az üzleti szabályokat ellenőrizzük.
        if (userRepository.findByEmail(req.email()).isPresent()) {
            throw new FieldConflictException("email",
                    "error.user.email.conflict", new Object[]{req.email()},
                    "Email already in use: " + req.email());
        }

        log.debug("Calling Keycloak to create user...");
        String keycloakUserId = keycloakUserAdminService.createUser(
                req.email().trim(),
                req.firstName().trim(),
                req.lastName().trim(),
                req.password(),
                UserRole.valueOf(req.role().toUpperCase())
        );
        log.debug("User created in Keycloak with ID: {}", keycloakUserId);

        User user = switch (req.role().toUpperCase()) {
            case "ADMIN"  -> { var u = new Admin();  u.setRole(UserRole.ADMIN);  yield u; }
            case "PLAYER" -> { var u = new Player(); u.setRole(UserRole.PLAYER); yield u; }
            case "COACH"  -> { var u = new Coach();  u.setRole(UserRole.COACH);  yield u; }
            case "FAN"    -> { var u = new Fan();    u.setRole(UserRole.FAN);    yield u; }
            default -> throw new BadRequestException("Unknown role: " + req.role());
        };

        user.setId(UUID.fromString(keycloakUserId));
        user.setEmail(req.email());
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());

        try {
            User savedUser = userRepository.save(user);
            auditEventService.record(
                    "USER_CREATED",
                    actorUserId,
                    savedUser.getRole() != null ? savedUser.getRole().name() : null,
                    "USER",
                    savedUser.getId().toString(),
                    "role=" + savedUser.getRole()
            );
            log.info("User successfully saved to the database with ID: {}", savedUser.getId());
            return toResponse(savedUser);
        } catch (RuntimeException ex) {
            try {
                keycloakUserAdminService.deleteUser(keycloakUserId);
            } catch (RuntimeException cleanupEx) {
                log.warn("Failed to rollback Keycloak user creation for userId={}", keycloakUserId, cleanupEx);
            }
            throw ex;
        }
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest req) {
        return updateUserInternal(id, req, null);
    }

    @Transactional
    public UserResponse updateMyUser(UpdateUserRequest req, Jwt jwt) {
        UUID actorUserId = resolveCurrentUserId(jwt);
        return updateUserInternal(actorUserId, req, actorUserId);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest req, Jwt jwt) {
        return updateUserInternal(id, req, resolveCurrentUserId(jwt));
    }

    private UserResponse updateUserInternal(UUID id, UpdateUserRequest req, UUID actorUserId) {
        log.debug("Updating user with ID: {}", id);
        User user = userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("error.user.not_found", new Object[]{id}, "User not found"));
        
        String oldFirstName = user.getFirstName();
        String oldLastName = user.getLastName();

        if (req.firstName() != null) user.setFirstName(req.firstName().trim());
        if (req.lastName() != null) user.setLastName(req.lastName().trim());
        
        log.debug("Syncing user update to Keycloak for ID: {}", id);
        keycloakUserAdminService.updateUser(
            id.toString(), 
            user.getFirstName(), 
            user.getLastName()
        );

        User savedUser = userRepository.save(user);
        
        String details = String.format("firstName: '%s' -> '%s', lastName: '%s' -> '%s'",
                oldFirstName, savedUser.getFirstName(),
                oldLastName, savedUser.getLastName());

        auditEventService.record(
                "USER_UPDATED",
                actorUserId,
                null,
                "USER",
                savedUser.getId().toString(),
                details
        );

        return toResponse(savedUser);
    }

    private UUID resolveCurrentUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new BadRequestException("error.auth.user_missing", new Object[0], "Authenticated token does not contain a subject.");
        }

        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("error.auth.invalid_subject", new Object[0], "Authenticated token subject is not a valid UUID.");
        }
    }

    /**
     * Töröl egy felhasználót a rendszerből a megadott azonosító alapján.
     *
     * @param id a törlendő felhasználó egyedi azonosítója
     * @param jwt az akciót végrehajtó felhasználó (admin) tokenje
     * @throws NotFoundException ha a felhasználó nem létezik az adott UUID-vel
     */
    @Transactional
    public void deleteUser(UUID id, Jwt jwt) {
        UUID actorUserId = resolveCurrentUserId(jwt);
        log.debug("Attempting to delete user with ID: {} by actor: {}", id, actorUserId);
        
        if (!userRepository.existsById(id)) {
            throw new NotFoundException("error.user.not_found", new Object[]{id}, "User not found: " + id);
        }
        
        userRepository.deleteById(id);
        log.debug("User {} deleted from local database. Calling Keycloak to delete user...", id);
        try {
            keycloakUserAdminService.deleteUser(id.toString());
        } catch (Exception ex) {
            log.warn("Failed to delete user in Keycloak with ID: {}", id, ex);
            // Optionally re-throw or ignore depending on business rules
        }

        auditEventService.record(
            "USER_DELETED",
            actorUserId,
            null,
            "USER",
            id.toString(),
            "deleted_from_local_db_and_keycloak=true"
        );
        log.info("User {} successfully deleted from database and Keycloak.", id);
    }

    /**
     * Belső segédmetódus, amely egy adatbázisos JPA User entitást alakít át a válaszhoz
     * használt UserResponse DTO objektummá. A felhasználó pontos alosztályától
     * (Player, Coach, Fan) függően kinyeri a vonatkozó csapattagságokat is.
     *
     * @param user az adatbázisból kiolvasott User entitás
     * @return a REST interfészen visszaadható DTO objektum
     */
    // ── Entitás → DTO konverzió ───────────────────────────────────────────────
    private UserResponse toResponse(User user) {
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
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.getCreatedAt(),
                teams
        );
    }
}
