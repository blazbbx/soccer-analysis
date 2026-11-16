package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ExternalServiceException;
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
import com.example.footballanalysis.model.responses.UserPageResponse;
import com.example.footballanalysis.model.responses.UserResponse;
import com.example.footballanalysis.paging.PageQuery;
import com.example.footballanalysis.repository.ClipRepository;
import com.example.footballanalysis.repository.ClubAdminMembershipRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.MatchSquadMemberRepository;
import com.example.footballanalysis.repository.TeamMessageRepository;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
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
    private final UserAccessService userAccessService;
    private final TeamMembershipQuery teamMembershipQuery;
    private final TeamRepository teamRepository;
    private final PlatformAdminDirectory platformAdminDirectory;

    /**
     * Visszaadja az összes regisztrált felhasználót a rendszerből.
     *
     * @return a felhasználók listája DTO formátumban
     */
    @Transactional(readOnly = true)
    public UserPageResponse getAllUsers(Integer page, Integer size) {
        return getAllUsers(page, size, null);
    }

    @Transactional(readOnly = true)
    public UserPageResponse getAllUsers(Integer page, Integer size, String query) {
        int resolvedPage = PageQuery.page(page);
        int resolvedSize = PageQuery.size(size);
        // A több szóköz egyre szűkül, hogy a „Kovács  Péter” is illeszkedjen a teljes névre.
        String needle = query == null ? "" : query.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        Page<User> result = needle.isEmpty()
                ? userRepository.findPage(PageQuery.of(resolvedPage, resolvedSize))
                : userRepository.findPageMatching(
                        "%" + escapeLike(needle) + "%",
                        PageQuery.of(resolvedPage, resolvedSize));
        List<User> users = result == null ? List.of() : result.getContent();
        long total = result == null ? 0 : result.getTotalElements();
        // Keycloak holds who is a platform admin; the users table keeps no role.
        // The count only steers the UI hint; deleting the last admin is refused by isLastVisiblePlatformAdmin.
        Set<UUID> platformAdmins = platformAdminDirectory.adminIdsForDisplay();
        return new UserPageResponse(
                users.stream().map(user -> toListItem(user, platformAdmins)).toList(),
                resolvedPage,
                resolvedSize,
                total,
                PageQuery.totalPages(total, resolvedSize),
                platformAdmins.size());
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

        // The role only goes to Keycloak; check it before the Keycloak account is made.
        UserRole role = switch (req.role().toUpperCase()) {
            case "ADMIN"  -> UserRole.ADMIN;
            case "PLAYER" -> UserRole.PLAYER;
            case "COACH"  -> UserRole.COACH;
            case "FAN"    -> UserRole.FAN;
            default -> throw new BadRequestException("error.user.role.invalid", new Object[0], "Unknown role: " + req.role());
        };

        log.debug("Calling Keycloak to create user...");
        String keycloakUserId = keycloakUserAdminService.createUser(
                req.email().trim(),
                req.firstName().trim(),
                req.lastName().trim(),
                req.password(),
                role
        );
        log.debug("User created in Keycloak with ID: {}", keycloakUserId);

        User user = new User();

        user.setId(UUID.fromString(keycloakUserId));
        user.setEmail(req.email());
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());

        try {
            User savedUser = userRepository.save(user);
            auditEventService.record(
                    "USER_CREATED",
                    actorUserId,
                    "USER",
                    savedUser.getId().toString(),
                    "role=" + role
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
        boolean nameChanged = req.firstName() != null || req.lastName() != null;
        if (nameChanged || req.dateOfBirth() != null) {
            playerProfileRepository.findByLinkedUser_Id(user.getId()).ifPresent(profile -> {
                if (req.dateOfBirth() != null) {
                    // A day of slack, so a birthday given in another time zone is not refused.
                    if (req.dateOfBirth().isAfter(java.time.LocalDate.now().plusDays(1))) {
                        throw new BadRequestException("error.user.date_of_birth.future", new Object[0],
                                "Date of birth cannot be in the future.");
                    }
                    // Age decides parental supervision and the sport medical rules, so a player cannot change
                    // their own date of birth once it is set. A club admin or the platform admin can.
                    boolean ownAccount = id.equals(actorUserId);
                    if (ownAccount && profile.getDateOfBirth() != null
                            && !profile.getDateOfBirth().equals(req.dateOfBirth())) {
                        throw new ForbiddenException("error.user.date_of_birth.locked", new Object[0],
                                "Your date of birth is already set. Ask your club admin to change it.");
                    }
                    User actor = actorUserId == null ? null : userRepository.findById(actorUserId).orElse(null);
                    if (actor != null) {
                        playerParentService.rejectClubAdminIfProfileOutsideClubs(profile, actor);
                    }
                    profile.setDateOfBirth(req.dateOfBirth());
                }
                // The roster, homework and medical lists read the profile's name, so it follows the account's.
                profile.copyNameFrom(user);
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

        if (isLastVisiblePlatformAdmin(user)) {
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
        // Extra hats live in the other subclass join table. Hibernate only clears the
        // collection mapped on the loaded subclass, so delete both by user id.
        teamMembershipQuery.deleteCoachTeamsForUser(id);
        teamMembershipQuery.deleteFanTeamsForUser(id);
        List<ClubAdminMembership> clubAdminMemberships = clubAdminMembershipRepository.findByUser_Id(id);
        if (clubAdminMemberships != null && !clubAdminMemberships.isEmpty()) {
            clubAdminMembershipRepository.deleteAll(clubAdminMemberships);
            clubAdminMembershipRepository.flush();
        }
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
            "USER",
            id.toString(),
            "deleted_from_local_db=true, deleted_from_keycloak=" + keycloakDeleted
        );
        log.info("User {} successfully deleted from database{}.", id, keycloakDeleted ? " and Keycloak" : "");
    }

    /**
     * Keycloak's admin client role is the only source of who is a platform admin.
     * If Keycloak cannot be asked, the delete is refused: deleting the last admin cannot be undone,
     * and the Keycloak account could not be removed during the outage anyway.
     */
    private boolean isLastVisiblePlatformAdmin(User user) {
        if (user.getId() == null) {
            return false;
        }
        try {
            if (!keycloakUserAdminService.hasClientRole(user.getId().toString(), UserRole.ADMIN)) {
                return false;
            }
            Set<String> adminIds = new LinkedHashSet<>();
            List<String> keycloakAdmins = keycloakUserAdminService.listUserIdsWithClientRole(UserRole.ADMIN);
            if (keycloakAdmins != null) {
                for (String id : keycloakAdmins) {
                    if (id != null && !id.isBlank()) {
                        adminIds.add(id);
                    }
                }
            }
            adminIds.add(user.getId().toString());
            return adminIds.size() <= 1;
        } catch (jakarta.ws.rs.NotFoundException gone) {
            // No Keycloak account any more: not an admin, the local row can go.
            return false;
        } catch (RuntimeException ex) {
            log.warn("Could not check the platform admins in Keycloak; refusing the delete", ex);
            throw new ExternalServiceException(
                    "error.keycloak.admin_check_failed",
                    new Object[0],
                    "The platform admins could not be checked in Keycloak. Try the delete again later.",
                    ex);
        }
    }

    /**
     * A user may edit their own name. A platform admin may edit anyone.
     * A club admin may edit a non-admin account that belongs to one of their clubs.
     * Whether the target is a platform admin comes from Keycloak, as for delete. The login role is never changed here.
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
        if (userAccessService.isAdmin(actor)) {
            return;
        }
        if (userAccessService.hasHat(actor, UserRole.CLUB_ADMIN)
                && sharesAdministeredClub(actor, target.getId())
                && !isPlatformAdmin(target)) {
            return;
        }
        throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied.");
    }

    /**
     * Platform admin in Keycloak. A missing Keycloak user is not an admin. If Keycloak cannot be asked,
     * the account counts as an admin, so a club admin cannot rename a platform admin during an outage.
     */
    private boolean isPlatformAdmin(User user) {
        if (user.getId() == null) {
            return false;
        }
        try {
            return keycloakUserAdminService.hasClientRole(user.getId().toString(), UserRole.ADMIN);
        } catch (jakarta.ws.rs.NotFoundException gone) {
            return false;
        } catch (RuntimeException ex) {
            log.warn("Could not check the Keycloak admin role; treating the account as a platform admin", ex);
            return true;
        }
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
            boolean inClub = !userRepository.findUserIdInClub(club.getId(), targetId).isEmpty();
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
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private UserResponse toListItem(User user, Set<UUID> platformAdmins) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getCreatedAt(),
                null,
                platformAdmins.contains(user.getId()),
                null);
    }

    private UserResponse toResponse(User user) {
        List<UserResponse.TeamInfo> teams = teamInfosForUser(user.getId());

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getCreatedAt(),
                teams
        );
    }

    private List<UserResponse.TeamInfo> teamInfosForUser(UUID userId) {
        java.util.LinkedHashSet<UUID> ids = new java.util.LinkedHashSet<>();
        ids.addAll(teamMembershipQuery.findCoachTeamIds(userId));
        ids.addAll(teamMembershipQuery.findFanTeamIds(userId));
        ids.addAll(playerProfileRepository.findTeamIdsByLinkedUserId(userId));
        if (ids.isEmpty()) {
            return null;
        }
        return teamRepository.findAllById(ids).stream()
                .map(team -> new UserResponse.TeamInfo(team.getId(), team.getName()))
                .toList();
    }
}
