package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.FieldConflictException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.InviteKind;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.TeamInvite;
import com.example.footballanalysis.model.db.user.*;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.requests.CreateChildLoginRequest;
import com.example.footballanalysis.model.requests.RegisterUserRequest;
import com.example.footballanalysis.model.responses.UserResponse;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.TeamInviteRepository;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final TeamInviteRepository teamInviteRepository;
    private final TeamService teamService;
    private final KeycloakUserAdminService keycloakUserAdminService;
    private final AuditEventService auditEventService;
    private final PlayerProfileRepository playerProfileRepository;
    private final PlayerParentService playerParentService;
    private final TeamInviteService teamInviteService;
    private final ParentInviteService parentInviteService;
    private final ClubAdminInviteService clubAdminInviteService;
    private final TeamMembershipQuery teamMembershipQuery;
    private final TeamRepository teamRepository;

    /**
     * Egy új felhasználó regisztrációját és egy csapathoz való meghívásának érvényesítését hajtja végre.
     * Ellenőrzi, hogy a meghívó token érvényes-e, létrehozza a felhasználót a Keycloak-ban és a helyi
     * adatbázisban, majd a megfelelő szerepkör (játékos, edző, rajongó) szerint hozzárendeli a felhasználót
     * a csapathoz. Hiba esetén visszavonja (törli) a Keycloak regisztrációt.
     *
     * @param request a regisztrációhoz szükséges adatok (például email, jelszó, név, meghívó token)
     * @return a regisztrált és mentett felhasználó adatai
     * @throws FieldConflictException ha a megadott e-mail címmel már regisztráltak
     * @throws RuntimeException különböző egyedi kivételek (pl. lejárt token) esetén, mely kiváltja a Keycloak fiók törlését
     */
    @Transactional
    public UserResponse registerWithInvite(RegisterUserRequest request) {
        log.debug("Registration attempt with invite token.");
        Optional<TeamInvite> found = teamInviteRepository.findByTokenForUpdate(request.inviteToken());
        if (found.isEmpty() || found.get().getTeam() == null) {
            if (parentInviteService.hasToken(request.inviteToken())) {
                return parentInviteService.register(request);
            }
            return clubAdminInviteService.register(request);
        }
        TeamInvite invite = findActiveInvite(request.inviteToken());
        boolean deferChild = defersChildDetails(invite);
        if (!deferChild) {
            teamInviteService.validateAcceptance(invite, request.acceptance());
        }

        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new FieldConflictException("email",
                    "error.user.email.conflict", new Object[]{request.email()},
                    "Email already in use: " + request.email());
        }

        log.debug("Creating user in Keycloak...");
        String keycloakUserId = keycloakUserAdminService.createUser(
                request.email().trim(),
                request.firstName().trim(),
                request.lastName().trim(),
                request.password(),
                invite.getInvitedRole()
        );
        log.info("Keycloak user created with ID: {}", keycloakUserId);

        try {
            User user = buildUser(invite.getInvitedRole());
            user.setId(UUID.fromString(keycloakUserId));
            user.setEmail(request.email().trim());
            user.setFirstName(request.firstName().trim());
            user.setLastName(request.lastName().trim());

            log.debug("Saving user to local database and adding to team...");
            User savedUser = userRepository.save(user);
            if (deferChild) {
                invite.setUsedCount(invite.getUsedCount() + 1);
                invite.reserveRegistration(savedUser.getId());
                teamInviteRepository.save(invite);
                auditEventService.record(
                        "PARENT_REGISTERED",
                        savedUser.getId(),
                        savedUser.getRole() != null ? savedUser.getRole().name() : null,
                        "TEAM",
                        invite.getTeam().getId().toString(),
                        "flow=registration-pending, inviteId=" + invite.getId()
                                + ", usedCount=" + invite.getUsedCount() + "/" + invite.getMaxUses());
                log.info("Parent {} registered. Child details stay on the invite token.", savedUser.getId());
                return toResponse(savedUser);
            }
            teamInviteService.fulfillInvite(invite, savedUser, request.acceptance());

            invite.setUsedCount(invite.getUsedCount() + 1);
            teamInviteRepository.save(invite);
            auditEventService.record(
                    "TEAM_INVITE_ACCEPTED",
                    savedUser.getId(),
                    savedUser.getRole() != null ? savedUser.getRole().name() : null,
                    "TEAM",
                    invite.getTeam().getId().toString(),
                    "flow=registration, inviteId=" + invite.getId()
                            + ", createdBy=" + invite.getCreatedByUserId()
                            + ", invitedRole=" + invite.getInvitedRole()
                            + ", usedCount=" + invite.getUsedCount() + "/" + invite.getMaxUses()
            );

            log.info("User {} successfully registered and added to team {}", savedUser.getId(), invite.getTeam().getId());
            return toResponse(savedUser);
        } catch (RuntimeException ex) {
            cleanupCreatedKeycloakUser(keycloakUserId);
            throw ex;
        }
    }

    /**
     * Szülő vagy admin a gyerek profiljához Keycloak + helyi PLAYER fiókot hoz létre,
     * és összeköti a keretprofillal. Meghívó link nélkül, a szülő sessionjéből.
     */
    @Transactional
    public UserResponse createChildLogin(UUID profileId, CreateChildLoginRequest request, User actor) {
        PlayerProfile profile = teamInviteService.requireEligibleChildLogin(profileId, actor);
        String email = request.email().trim();
        if (actor.getEmail() != null && actor.getEmail().equalsIgnoreCase(email)) {
            throw new FieldConflictException(
                    "email",
                    "error.player_profile.child_email_own",
                    new Object[0],
                    "Use a different email for the child's login.");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new FieldConflictException("email",
                    "error.user.email.conflict", new Object[]{email},
                    "Email already in use: " + email);
        }

        String firstName = resolveAccountName(request.firstName(), profile.getFirstName());
        String lastName = resolveAccountName(request.lastName(), profile.getLastName());

        String keycloakUserId = keycloakUserAdminService.createUser(
                email,
                firstName,
                lastName,
                request.password(),
                UserRole.PLAYER
        );
        try {
            Player child = new Player();
            child.setRole(UserRole.PLAYER);
            child.setId(UUID.fromString(keycloakUserId));
            child.setEmail(email);
            child.setFirstName(firstName);
            child.setLastName(lastName);
            User savedUser = userRepository.save(child);
            playerParentService.attachChildLogin(savedUser, profile);
            auditEventService.record(
                    "CHILD_LOGIN_CREATED",
                    actor.getId(),
                    actor.getRole() != null ? actor.getRole().name() : null,
                    "PLAYER_PROFILE",
                    profile.getId().toString(),
                    "childUserId=" + savedUser.getId()
            );
            return toResponse(savedUser);
        } catch (RuntimeException ex) {
            cleanupCreatedKeycloakUser(keycloakUserId);
            throw ex;
        }
    }

    private static String resolveAccountName(String requested, String profileValue) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        if (profileValue != null && !profileValue.isBlank()) {
            return profileValue.trim();
        }
        throw new BadRequestException(
                "error.team.invite.child_required",
                new Object[0],
                "Child name is required.");
    }

    /**
     * Törli a Keycloak-ból az újonnan létrehozott felhasználót abban az esetben, ha
     * a folyamat közben (például a helyi adatbázisba való mentéskor) valamilyen hiba történt.
     * 
     * @param keycloakUserId a törlendő Keycloak fiók azonosítója
     */
    private void cleanupCreatedKeycloakUser(String keycloakUserId) {
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            return;
        }

        try {
            log.warn("Executing Keycloak user cleanup for ID: {}", keycloakUserId);
            keycloakUserAdminService.deleteUser(keycloakUserId);
            log.info("Keycloak user cleanup successful for ID: {}", keycloakUserId);
        } catch (RuntimeException cleanupEx) {
            log.error("Failed to delete Keycloak user during cleanup. ID: {}", keycloakUserId, cleanupEx);
            // best-effort cleanup: az eredeti hiba fontosabb, mint a törlési hiba
        }
    }

    private boolean defersChildDetails(TeamInvite invite) {
        if (invite.getPlayerProfile() != null) {
            return false;
        }
        InviteKind kind = invite.getInviteKind() != null
                ? invite.getInviteKind()
                : InviteKind.fromRole(invite.getInvitedRole());
        return kind == InviteKind.PARENT;
    }

    /**
     * Megkeresi az adatbázisban a meghívó tokent és validálja annak érvényességét.
     * Ellenőrzi, hogy a token létezik-e, valamint hogy nem járt-e még le,
     * és van-e rajta még felhasználható alkalom (nincs-e kimerítve).
     *
     * @param token a meghívó hivatkozásban szereplő 16 karakteres azonosító
     * @return a megtalált és aktív TeamInvite entitás
     * @throws NotFoundException ha a meghívó nem található
     * @throws BadRequestException ha a meghívó lejárt vagy már felhasználták mindet
     */
    private TeamInvite findActiveInvite(String token) {
        log.debug("Validating invite token...");
        TeamInvite invite = teamInviteRepository.findByTokenForUpdate(token)
                .orElseThrow(() -> new NotFoundException("error.team.invite.not_found", new Object[0], "Invite not found."));

        if (invite.isExpired()) {
            throw new BadRequestException("error.team.invite.expired", new Object[]{invite.getId()}, "This invite link has expired. inviteId=" + invite.getId());
        }
        if (invite.isExhausted()) {
            throw new BadRequestException("error.team.invite.exhausted", new Object[]{invite.getId()}, "This invite link has already been used. inviteId=" + invite.getId());
        }

        log.debug("Invite token valid for team: {}", invite.getTeam().getId());
        return invite;
    }

    /**
     * Segédmetódus a megfelelő JPA User alosztály (Player, Coach vagy Fan)
     * példányosításához a kért szerepkör (UserRole) alapján.
     *
     * @param role az elvárt szerepkör a meghívó alapján
     * @return az inicializált, még adatbázisba nem mentett User (vagy valamelyik alosztályának) példánya
     * @throws ConflictException ha meghívó alapján adminisztrátort próbálnának létrehozni
     */
    private User buildUser(UserRole role) {
        return switch (role) {
            case PLAYER -> { var user = new Player(); user.setRole(UserRole.PLAYER); yield user; }
            case COACH -> { var user = new Coach(); user.setRole(UserRole.COACH); yield user; }
            case FAN -> { var user = new Fan(); user.setRole(UserRole.FAN); yield user; }
            case PARENT -> { var user = new Parent(); user.setRole(UserRole.PARENT); yield user; }
            case ADMIN, CLUB_ADMIN -> throw new ConflictException("error.team.invite.invalid_role", new Object[]{role}, "Invites cannot be created for " + role + " users.");
        };
    }

    /**
     * Hozzáadja az újonnan regisztrált felhasználót a meghívóban szereplő csapathoz,
     * megbízva ezzel a TeamService megfelelő metódusát a szerepkör alapján.
     *
     * @param invite az érvényesített meghívó entitás
     * @param user a már adatbázisba elmentett JPA felhasználó entitás
     */
    /**
     * Egy adatbázisos JPA User entitást alakít át a válaszhoz
     * használt UserResponse DTO objektummá, legenerálva a csapatok listáját is.
     *
     * @param user az adatbázisból származó User entitás
     * @return a REST interfészen visszaadható DTO objektum
     */
    private UserResponse toResponse(User user) {
        java.util.LinkedHashSet<UUID> ids = new java.util.LinkedHashSet<>();
        ids.addAll(teamMembershipQuery.findCoachTeamIds(user.getId()));
        ids.addAll(teamMembershipQuery.findFanTeamIds(user.getId()));
        ids.addAll(playerProfileRepository.findTeamIdsByLinkedUserId(user.getId()));
        List<UserResponse.TeamInfo> teams = ids.isEmpty()
                ? null
                : teamRepository.findAllById(ids).stream()
                        .map(team -> new UserResponse.TeamInfo(team.getId(), team.getName()))
                        .toList();

        return new UserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.getRole(), user.getCreatedAt(), teams);
    }
}