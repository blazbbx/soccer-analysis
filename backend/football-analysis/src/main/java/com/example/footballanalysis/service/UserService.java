package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.FieldConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.ClubAdminMembership;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.db.user.*;
import com.example.footballanalysis.model.requests.CreateUserRequest;
import com.example.footballanalysis.model.requests.UpdateUserRequest;
import com.example.footballanalysis.model.responses.UserResponse;
import com.example.footballanalysis.repository.ClipRepository;
import com.example.footballanalysis.repository.ClubAdminMembershipRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.MatchSquadMemberRepository;
import com.example.footballanalysis.repository.TeamMessageRepository;
import com.example.footballanalysis.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final TeamMessageRepository teamMessageRepository;
    private final MatchRepository matchRepository;
    private final ClipRepository clipRepository;
    private final MatchSquadMemberRepository matchSquadMemberRepository;
    private final KeycloakUserAdminService keycloakUserAdminService;
    private final AuditEventService auditEventService;
    private final PlayerProfileRepository playerProfileRepository;
    private final PlayerParentService playerParentService;
    private final ClubAdminMembershipRepository clubAdminMembershipRepository;

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
            case "PARENT", "CLUB_ADMIN" -> throw new BadRequestException("error.user.role.invalid", new Object[0], "Unknown role: " + req.role());
            default -> throw new BadRequestException("error.user.role.invalid", new Object[0], "Unknown role: " + req.role());
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
        requireCanEdit(actorUserId, user);

        String oldFirstName = user.getFirstName();
        String oldLastName = user.getLastName();

        if (req.firstName() != null) user.setFirstName(req.firstName().trim());
        if (req.lastName() != null) user.setLastName(req.lastName().trim());
        if (req.dateOfBirth() != null && user.getRole() == UserRole.PLAYER) {
            playerProfileRepository.findByLinkedUser_Id(user.getId()).ifPresent(profile -> {
                profile.setDateOfBirth(req.dateOfBirth());
                playerProfileRepository.save(profile);
            });
        }
        
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
        deleteUser(id, resolveCurrentUserId(jwt));
    }

    @Transactional
    public void deleteUser(UUID id, User actor) {
        if (actor == null || actor.getId() == null) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required.");
        }
        deleteUser(id, actor.getId());
    }

    private void deleteUser(UUID id, UUID actorUserId) {
        log.debug("Attempting to delete user with ID: {} by actor: {}", id, actorUserId);
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("error.user.not_found", new Object[]{id}, "User not found: " + id));

        if (id.equals(actorUserId)) {
            throw new BadRequestException(
                    "error.user.self_delete",
                    new Object[0],
                    "You cannot delete your own account"
            );
        }

        if (user.getRole() == UserRole.ADMIN && userRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new BadRequestException(
                    "error.user.last_admin",
                    new Object[0],
                    "Cannot delete the last remaining admin"
            );
        }

        requireNotLastClubAdmin(id);

        // Break FKs that would otherwise block deleting the user row.
        int deletedMessages = teamMessageRepository.deleteBySenderId(id);
        int clearedUploads = matchRepository.clearUploadedByUserId(id);
        int clearedClips = clipRepository.clearCreatedByUserId(id);
        matchSquadMemberRepository.deleteAllByPlayer_Id(id);
        playerParentService.detachUser(id);
        log.debug(
                "Cleared user {} references: teamMessages={}, matchesUploadedBy={}, clipsCreatedBy={}",
                id, deletedMessages, clearedUploads, clearedClips
        );

        userRepository.deleteById(id);
        // Flush before the Keycloak call so a DB constraint failure does not leave
        // the identity provider user already deleted.
        userRepository.flush();
        log.debug("User {} deleted from local database. Calling Keycloak to delete user...", id);
        boolean keycloakDeleted = true;
        try {
            keycloakUserAdminService.deleteUser(id.toString());
        } catch (Exception ex) {
            keycloakDeleted = false;
            log.warn("Failed to delete user in Keycloak with ID: {}", id, ex);
        }

        auditEventService.record(
            "USER_DELETED",
            actorUserId,
            null,
            "USER",
            id.toString(),
            "deleted_from_local_db=true, deleted_from_keycloak=" + keycloakDeleted
        );
        log.info("User {} successfully deleted from database{}.", id, keycloakDeleted ? " and Keycloak" : "");
    }

    /**
     * A user may edit their own name. A platform admin may edit anyone.
     * A club admin may edit a non-admin account that belongs to one of their clubs.
     * The login role is never changed here.
     */
    private void requireCanEdit(UUID actorUserId, User target) {
        if (actorUserId.equals(target.getId())) {
            return;
        }
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new NotFoundException(
                        "error.user.not_found",
                        new Object[]{actorUserId},
                        "User not found"));
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }
        if (actor.getRole() == UserRole.CLUB_ADMIN
                && target.getRole() != UserRole.ADMIN
                && sharesAdministeredClub(actor, target.getId())) {
            return;
        }
        throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied.");
    }

    /**
     * Same last-admin lock as club-admin remove: CASCADE would otherwise empty the club.
     */
    private void requireNotLastClubAdmin(UUID userId) {
        for (ClubAdminMembership membership : clubAdminMembershipRepository.findByUser_Id(userId)) {
            Club club = membership.getClub();
            if (club == null || club.getId() == null) {
                continue;
            }
            List<ClubAdminMembership> locked = clubAdminMembershipRepository.findByClub_IdForUpdate(club.getId());
            if (locked.size() <= 1) {
                throw new ConflictException(
                        "error.club_admin.last",
                        new Object[0],
                        "The last club admin cannot be removed.");
            }
        }
    }

    private boolean sharesAdministeredClub(User actor, UUID targetId) {
        for (ClubAdminMembership membership : clubAdminMembershipRepository.findByUser_Id(actor.getId())) {
            Club club = membership.getClub();
            if (club == null || club.getId() == null) {
                continue;
            }
            boolean inClub = userRepository.findUsersInClub(club.getId()).stream()
                    .anyMatch(member -> targetId.equals(member.getId()));
            if (inClub) {
                return true;
            }
        }
        return false;
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
            teams = teamInfoForLinkedProfile(p.getId());
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

    private List<UserResponse.TeamInfo> teamInfoForLinkedProfile(UUID userId) {
        Optional<PlayerProfile> profile = playerProfileRepository.findByLinkedUser_Id(userId);
        if (profile == null || profile.isEmpty()) {
            return List.of();
        }
        return playerProfileRepository.findTeamsByLinkedUserId(userId).stream()
                .map(team -> new UserResponse.TeamInfo(team.getId(), team.getName()))
                .toList();
    }
}
