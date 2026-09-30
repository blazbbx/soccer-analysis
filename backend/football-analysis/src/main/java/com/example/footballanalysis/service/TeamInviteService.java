package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.InviteKind;
import com.example.footballanalysis.model.db.PlayerParentId;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.TeamInvite;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;

import com.example.footballanalysis.model.requests.AcceptInviteRequest;
import com.example.footballanalysis.model.responses.InviteTokenResponse;
import com.example.footballanalysis.model.responses.TeamInviteResponse;
import com.example.footballanalysis.repository.PlayerParentRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.TeamInviteRepository;
import com.example.footballanalysis.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamInviteService {


    // --- Meghívó korlátok és lejárati idők alapértelmezései ---
    private static final int GENERATION_MAX_USES = 20;
    private static final int GENERATION_EXPIRATION_HOURS = 24;

    private final TeamRepository teamRepository;
    private final TeamInviteRepository teamInviteRepository;
    private final TeamService teamService;
    private final AuditEventService auditEventService;
    private final PlayerProfileRepository playerProfileRepository;
    private final PlayerParentRepository playerParentRepository;
    private final PlayerParentService playerParentService;
    private final ParentInviteService parentInviteService;
    private final ClubAdminInviteService clubAdminInviteService;
    private final UserAccessService userAccessService;

    /**
    * Meghívó token generálása egy adott csapathoz egy bizonyos szerepkörre.
     *
     * @param teamId A csapat egyedi azonosítója, amelyhez a meghívó készül.
     * @param actor A hívást kezdeményező hitelesített felhasználó (User objektum).
     * @param requestedRole A meghívott felhasználó kért szerepköre (alapértelmezetten PLAYER).
     * @return Az elkészített meghívó tokent tartalmazó válasz.
     */
    @Transactional
    public InviteTokenResponse generateInviteToken(UUID teamId, User actor, UserRole requestedRole) {
        InviteKind resolvedKind = InviteKind.fromRole(requestedRole);
        if (resolvedKind == null) {
            throw new BadRequestException("error.team.invite.role_required", new Object[0], "Invite role must be provided.");
        }

        Set<String> actorRoles = extractActorRoles(actor);
        TeamInvite invite = createInviteEntity(teamId, actor, actorRoles, resolvedKind, LocalDateTime.now().plusHours(GENERATION_EXPIRATION_HOURS));
        teamInviteRepository.save(invite);
        auditEventService.record(
            "TEAM_INVITE_GENERATED",
            invite.getCreatedByUserId(),
            invite.getCreatedByUserRole() != null ? invite.getCreatedByUserRole().name() : null,
            "TEAM",
            teamId.toString(),
            "requestedRole=" + invite.getInvitedRole() + ", kind=" + invite.getInviteKind() + ", maxUses=" + invite.getMaxUses() + ", expiresAt=" + invite.getExpiresAt()
        );
        log.atInfo()
                .setMessage("Generated new team invite link for teamId={}, requestedRole={}, createdBy={}, createdByRole={}")
                .addArgument(teamId)
                .addArgument(invite.getInvitedRole())
                .addArgument(invite.getCreatedByUserId())
                .addArgument(invite.getCreatedByUserRole())
                .addKeyValue("event_type", "TEAM_INVITE_GENERATED")
                .addKeyValue("team_id", teamId)
                .addKeyValue("requested_role", requestedRole)
                .addKeyValue("created_by_user_id", invite.getCreatedByUserId())
                .addKeyValue("created_by_user_role", invite.getCreatedByUserRole())
                .log();
        return new InviteTokenResponse(invite.getToken());
    }

    /**
     * Belső segédfüggvény: létrehozza a meghívó (TeamInvite) entitást az adatbázis számára.
     * Megvizsgálja az actor User objektumot, hogy ADMIN jogkörrel rendelkezik-e.
     * Ha nem ADMIN, akkor csak úgy engedi meghívni az új tagot, ha az actor beazonosítható, mint csapat edzője vagy játékosa.
     *
     * @param teamId A csapat azonosítója.
     * @param actor A hitelesített aktor (User objektum) a jogosultságok vizsgálatához.
     * @param actorRoles Az actor szerepkörei.
     * @param invitedRole A szerepkör, amellyel a felhasználót meghívják. ADMIN nem lehet.
     * @param expiresAt A meghívó lejárati ideje.
     * @return A mentésre kész TeamInvite entitás.
     */
    private TeamInvite createInviteEntity(UUID teamId, User actor, Set<String> actorRoles, InviteKind kind, LocalDateTime expiresAt) {
        UserRole invitedRole = kind.invitedRole();
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found: " + teamId));

        // Szerepkörök vizsgálata (megnézzük, hogy admin-e egyáltalán)
        boolean isAdmin = actorRoles.contains(UserRole.ADMIN.name());

        // Ha az aktor adminisztrátor, nem kell ellenőrizzük a csapathoz tartozást
        if (!isAdmin) {
            if (actorRoles.contains(UserRole.COACH.name())) {
                if (!(actor instanceof Coach coach)) {
                    throw new BadRequestException("error.team.invalid_actor", new Object[0], "Actor is not a coach.");
                }
                ensureCoachAssignedToTeam(team, teamId, coach);
            } else if (isPlayerActor(actorRoles)) {
                if (!(actor instanceof Player player)) {
                    throw new BadRequestException("error.team.invalid_actor", new Object[0], "Actor is not a player.");
                }
                ensurePlayerAssignedToTeam(team, teamId, player);
                ensurePlayerCanOnlyInviteFans(invitedRole);
            } else if (isParentActor(actorRoles)) {
                if (!(actor instanceof Parent)) {
                    throw new BadRequestException("error.team.invalid_actor", new Object[0], "Actor is not a parent.");
                }
                ensureParentAssignedToTeam(actor.getId(), teamId);
                ensurePlayerCanOnlyInviteFans(invitedRole);
            } else if (actorRoles.contains(UserRole.CLUB_ADMIN.name())) {
                UUID clubId = team.getClub() != null ? team.getClub().getId() : null;
                if (!userAccessService.isClubAdmin(actor, clubId)) {
                    throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied.");
                }
            } else {
                throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied.");
            }
        }

        // Biztonsági okokból adminba nem hívhatunk meg senkit! Ezt kizárjuk.
        if (invitedRole == UserRole.ADMIN) {
            throw new BadRequestException("error.team.invite.invalid_role", new Object[]{invitedRole}, "Invites cannot be created for ADMIN users.");
        }

        TeamInvite invite = new TeamInvite();
        invite.setTeam(team);
        invite.setCreatedByUserId(actor.getId());
        invite.setCreatedByUserRole(resolveActorRole(actorRoles));
        invite.setInvitedRole(invitedRole);
        invite.setInviteKind(kind);
        invite.setToken(UUID.randomUUID().toString());
        invite.setMaxUses(GENERATION_MAX_USES);
        invite.setUsedCount(0);
        invite.setExpiresAt(expiresAt);
        return invite;
    }

    private void ensureCoachAssignedToTeam(Team team, UUID teamId, Coach coach) {
        UUID coachId = coach.getId();
        // Csak és kizárólag a csapathoz tartozó edző generálhat csapattagok felvételére lehetőséget!
        if (coachId == null || team.getCoaches().stream().map(Coach::getId).noneMatch(coachId::equals)) {
            throw new ConflictException("error.team.invite.not_authorized", new Object[]{teamId, coach.getId()}, "Requester is not assigned to this team.");
        }
    }

    private void ensurePlayerAssignedToTeam(Team team, UUID teamId, Player player) {
        UUID playerId = player.getId();
        if (playerId == null || !playerProfileRepository.existsByLinkedUser_IdAndTeam_Id(playerId, teamId)) {
            throw new ConflictException("error.team.invite.not_authorized", new Object[]{teamId, player.getId()}, "Requester is not assigned to this team.");
        }
    }

    private void ensureParentAssignedToTeam(UUID parentId, UUID teamId) {
        if (parentId == null || !playerParentRepository.existsByUser_IdAndPlayerProfile_Team_IdIn(parentId, List.of(teamId))) {
            throw new ConflictException("error.team.invite.not_authorized", new Object[]{teamId, parentId}, "Requester is not assigned to this team.");
        }
    }

    private void ensurePlayerCanOnlyInviteFans(UserRole invitedRole) {
        if (invitedRole != UserRole.FAN) {
            throw new BadRequestException("error.team.invite.player_only_fan", new Object[]{invitedRole}, "Players can only create invites for FAN users.");
        }
    }

    /**
     * Segédfüggvény: Kinyeri az adott usertől a JWT formátumú Keycloak szimpla jogosultságait.
     * Mind a realm_access, mind a resource_access kliens szintűeket összesíti egy sima listába.
     *
     * @return A kinyert és normalizált szerepkörök (role-ok) halmaza.
     */
    private Set<String> extractActorRoles(User actor) {
        Set<String> roles = new LinkedHashSet<>();
        if (actor != null && actor.getRole() != null) {
            roles.add(actor.getRole().name());
        }
        return roles;
    }

    private UserRole resolveActorRole(Set<String> actorRoles) {
        if (actorRoles.contains(UserRole.ADMIN.name())) {
            return UserRole.ADMIN;
        }
        if (actorRoles.contains(UserRole.COACH.name())) {
            return UserRole.COACH;
        }
        if (actorRoles.contains(UserRole.PLAYER.name())) {
            return UserRole.PLAYER;
        }
        if (actorRoles.contains(UserRole.PARENT.name())) {
            return UserRole.PARENT;
        }
        if (actorRoles.contains(UserRole.FAN.name())) {
            return UserRole.FAN;
        }
        if (actorRoles.contains(UserRole.CLUB_ADMIN.name())) {
            return UserRole.CLUB_ADMIN;
        }

        throw new BadRequestException("error.auth.role_missing", new Object[0], "Authenticated token does not contain a supported role.");
    }

    private boolean isPlayerActor(Set<String> actorRoles) {
        return actorRoles.contains(UserRole.PLAYER.name())
                && !actorRoles.contains(UserRole.COACH.name())
                && !actorRoles.contains(UserRole.ADMIN.name());
    }

    private boolean isParentActor(Set<String> actorRoles) {
        return actorRoles.contains(UserRole.PARENT.name())
                && !actorRoles.contains(UserRole.COACH.name())
                && !actorRoles.contains(UserRole.ADMIN.name())
                && !actorRoles.contains(UserRole.PLAYER.name());
    }

    /**
     * Lekérdezi egy aktív meghívó adatait a megadott token alapján.
     *
     * @param token A meghívó egyedi, token formátumú azonosítója.
     * @return A meghívó részletes adatait tartalmazó válasz DTO.
     */
    @Transactional
    public TeamInviteResponse getInvite(String token) {
        Optional<TeamInvite> teamInvite = teamInviteRepository.findByToken(token);
        if (teamInvite.isPresent() && teamInvite.get().getTeam() != null) {
            return toResponse(findActiveInvite(token));
        }
        if (parentInviteService.hasToken(token)) {
            return parentInviteService.getInvite(token);
        }
        return clubAdminInviteService.getInvite(token);
    }

    /**
     * Elfogad egy meghívót a token alapján, és hozzáadja az actor User-t a csapathoz.
     *
     * @param token A meghívó egyedi tokenje.
     * @param actor A hitelesített, meghívót elfogadó felhasználó (User objektum).
     * @return A meghívó frissített adatait tartalmazó válasz.
     */
    @Transactional
    public TeamInviteResponse acceptInvite(String token, User actor) {
        return acceptInvite(token, actor, AcceptInviteRequest.empty());
    }

    @Transactional
    public TeamInviteResponse acceptInvite(String token, User actor, AcceptInviteRequest details) {
        if (actor == null) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required to accept an invite.");
        }
        Optional<TeamInvite> found = teamInviteRepository.findByTokenForUpdate(token);
        if (found.isEmpty() || found.get().getTeam() == null) {
            if (parentInviteService.hasToken(token)) {
                return parentInviteService.accept(token, actor);
            }
            return clubAdminInviteService.accept(token, actor);
        }
        TeamInvite invite = findActiveInviteForUpdate(token);
        InviteKind kind = resolvedKind(invite);

        if (kind != InviteKind.PARENT
                && (actor.getRole() == null || actor.getRole() != invite.getInvitedRole())) {
            throw new BadRequestException(
                    "error.team.invite.role_mismatch",
                    new Object[]{actor.getRole(), invite.getInvitedRole()},
                    "Authenticated user role does not match the invite role.");
        }

        if (invite.isExhausted()) {
            throw new ConflictException("error.team.invite.exhausted", new Object[]{invite.getId()}, "This invite link has reached its maximum number of uses. inviteId=" + invite.getId());
        }

        fulfillInvite(invite, actor, details);

        UUID userId = actor.getId();
        invite.setUsedCount(invite.getUsedCount() + 1);
        auditEventService.record(
            "TEAM_INVITE_ACCEPTED",
            userId,
            invite.getInvitedRole() != null ? invite.getInvitedRole().name() : null,
            "TEAM",
            invite.getTeam().getId().toString(),
            "flow=direct_accept, inviteId=" + invite.getId()
                    + ", createdBy=" + invite.getCreatedByUserId()
                    + ", invitedRole=" + invite.getInvitedRole()
                    + ", usedCount=" + invite.getUsedCount() + "/" + invite.getMaxUses()
        );
        log.atInfo()
                .setMessage("Invite accepted by userId {}, added to teamId {} as role {}. Used {}/{} times.")
                .addArgument(userId)
                .addArgument(invite.getTeam().getId())
                .addArgument(invite.getInvitedRole())
                .addArgument(invite.getUsedCount())
                .addArgument(invite.getMaxUses())
                .addKeyValue("event_type", "TEAM_INVITE_ACCEPTED")
                .addKeyValue("accepted_by_user_id", userId)
                .addKeyValue("invite_id", invite.getId())
                .addKeyValue("created_by_user_id", invite.getCreatedByUserId())
                .addKeyValue("team_id", invite.getTeam().getId())
                .addKeyValue("assigned_role", invite.getInvitedRole())
                .addKeyValue("used_count", invite.getUsedCount())
                .addKeyValue("max_uses", invite.getMaxUses())
                .log();
        return toResponse(teamInviteRepository.save(invite));
    }

    public PlayerProfile requireEligibleChildLogin(UUID profileId, User actor) {
        if (actor == null || actor.getId() == null) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required.");
        }
        PlayerProfile profile = playerProfileRepository.findById(profileId)
                .orElseThrow(() -> new NotFoundException(
                        "error.player_profile.not_found", new Object[]{profileId}, "Player profile not found."));
        boolean admin = actor.getRole() == UserRole.ADMIN;
        boolean clubAdmin = userAccessService.isClubAdminOfProfile(actor, profileId);
        boolean parentOfChild = playerParentRepository.existsById(
                new com.example.footballanalysis.model.db.PlayerParentId(actor.getId(), profileId));
        if (!admin && !clubAdmin && !parentOfChild) {
            throw new ForbiddenException("error.auth.forbidden", new Object[0], "Only a parent of this player can create a child login.");
        }
        if (playerProfileRepository.findTeamsByProfileId(profileId).isEmpty()) {
            throw new ConflictException("error.team.invite.profile_not_on_team", new Object[]{profileId}, "Player profile is not on a team.");
        }
        if (profile.getLinkedUser() != null) {
            throw new ConflictException("error.player_profile.login_exists", new Object[0], "This player profile already has a login.");
        }
        return profile;
    }

    @Transactional
    public InviteTokenResponse createChildLoginInvite(UUID profileId, User actor) {
        PlayerProfile profile = requireEligibleChildLogin(profileId, actor);

        TeamInvite invite = new TeamInvite();
        invite.setTeam(playerProfileRepository.findTeamsByProfileId(profile.getId()).get(0));
        invite.setPlayerProfile(profile);
        invite.setCreatedByUserId(actor.getId());
        invite.setCreatedByUserRole(actor.getRole());
        invite.setInviteKind(InviteKind.CHILD_LOGIN);
        invite.setInvitedRole(UserRole.PLAYER);
        invite.setToken(UUID.randomUUID().toString());
        invite.setMaxUses(1);
        invite.setUsedCount(0);
        invite.setExpiresAt(LocalDateTime.now().plusHours(GENERATION_EXPIRATION_HOURS));
        teamInviteRepository.save(invite);
        return new InviteTokenResponse(invite.getToken());
    }

    public void validateAcceptance(TeamInvite invite, AcceptInviteRequest details) {
        AcceptInviteRequest safe = details == null ? AcceptInviteRequest.empty() : details;
        switch (resolvedKind(invite)) {
            case PARENT -> {
                if (safe.existingProfileId() == null) {
                    requireChildDetails(safe);
                }
            }
            case PLAYER_SELF -> requireAdult(safe.dateOfBirth());
            case CHILD_LOGIN -> {
                if (invite.getPlayerProfile() == null) {
                    throw new BadRequestException("error.team.invite.profile_required", new Object[0], "Child login needs a player profile.");
                }
                if (invite.getPlayerProfile().getLinkedUser() != null) {
                    throw new ConflictException("error.player_profile.login_exists", new Object[0], "This player profile already has a login.");
                }
            }
            case COACH, FAN -> {
            }
            case CLUB_ADMIN -> throw new BadRequestException(
                    "error.team.invite.invalid_role", new Object[0], "Club admin invites are not team invites.");
        }
    }

    @Transactional
    public void fulfillInvite(TeamInvite invite, User user, AcceptInviteRequest details) {
        AcceptInviteRequest safe = details == null ? AcceptInviteRequest.empty() : details;
        validateAcceptance(invite, safe);
        switch (resolvedKind(invite)) {
            case PARENT -> {
                if (safe.existingProfileId() != null) {
                    attachExistingChild(invite, user, safe.existingProfileId());
                } else {
                    PlayerProfile created = teamService.addRosterProfile(
                            invite.getTeam().getId(),
                            safe.childFirstName(),
                            safe.childLastName(),
                            safe.childDateOfBirth());
                    playerParentService.link(user, created);
                }
            }
            case PLAYER_SELF -> {
                teamService.addPlayerToTeam(invite.getTeam().getId(), user.getId(), user, false);
                teamService.assignLinkedDateOfBirth(user.getId(), safe.dateOfBirth());
            }
            case CHILD_LOGIN -> playerParentService.attachChildLogin(user, invite.getPlayerProfile());
            case COACH -> teamService.addCoachToTeam(invite.getTeam().getId(), user.getId(), user, false);
            case FAN -> teamService.addFanToTeam(invite.getTeam().getId(), user.getId(), user, false);
            case CLUB_ADMIN -> throw new BadRequestException(
                    "error.team.invite.invalid_role", new Object[0], "Club admin invites are not team invites.");
        }
    }

    private void attachExistingChild(TeamInvite invite, User parent, UUID profileId) {
        PlayerProfile profile = playerProfileRepository.findById(profileId)
                .orElseThrow(() -> new NotFoundException(
                        "error.player_profile.not_found",
                        new Object[]{profileId},
                        "Player profile not found."));
        if (parent.getId() == null || !playerParentRepository.existsById(new PlayerParentId(parent.getId(), profileId))) {
            throw new ForbiddenException(
                    "error.player_parent.not_linked",
                    new Object[0],
                    "This child is not linked to the signed-in parent.");
        }
        teamService.addProfileToTeam(profile, invite.getTeam());
    }

    private void requireChildDetails(AcceptInviteRequest details) {
        if (details.childFirstName() == null || details.childFirstName().isBlank()
                || details.childLastName() == null || details.childLastName().isBlank()
                || details.childDateOfBirth() == null) {
            throw new BadRequestException("error.team.invite.child_required", new Object[0], "Child name and date of birth are required.");
        }
        if (details.childDateOfBirth().isAfter(LocalDate.now())) {
            throw new BadRequestException("error.team.invite.child_dob_future", new Object[0], "Child date of birth cannot be in the future.");
        }
    }

    private void requireAdult(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            throw new BadRequestException("error.team.invite.dob_required", new Object[0], "Date of birth is required.");
        }
        if (dateOfBirth.isAfter(LocalDate.now()) || Period.between(dateOfBirth, LocalDate.now()).getYears() < 16) {
            throw new BadRequestException(
                    "error.team.invite.underage",
                    new Object[0],
                    "A player account requires age 16 or older. Use a parent invite.");
        }
    }

    private InviteKind resolvedKind(TeamInvite invite) {
        if (invite.getInviteKind() != null) {
            return invite.getInviteKind();
        }
        InviteKind fromRole = InviteKind.fromRole(invite.getInvitedRole());
        if (fromRole == null) {
            throw new BadRequestException("error.team.invite.invalid_role", new Object[]{invite.getInvitedRole()}, "Unsupported invite role.");
        }
        return fromRole;
    }

    /**
     * Megkeres egy aktív (nem lejárt) meghívót a token alapján.
     *
     * @param token A meghívó tokenje.
     * @return A megtalált és aktív TeamInvite entitás.
     * @throws NotFoundException Ha a meghívó nem található.
     * @throws ConflictException Ha a meghívó lejárt.
     */
    private TeamInvite findActiveInvite(String token) {
        TeamInvite invite = teamInviteRepository.findByToken(token)
                .orElseThrow(() -> new NotFoundException("error.team.invite.not_found", new Object[0], "Invite not found."));

        if (invite.isExpired()) {
            throw new ConflictException("error.team.invite.expired", new Object[]{invite.getId()}, "This invite link has expired. inviteId=" + invite.getId());
        }
        log.debug("Invite validated for teamId {} and role {}", invite.getTeam().getId(), invite.getInvitedRole());
        return invite;
    }

    private TeamInvite findActiveInviteForUpdate(String token) {
        TeamInvite invite = teamInviteRepository.findByTokenForUpdate(token)
                .orElseThrow(() -> new NotFoundException("error.team.invite.not_found", new Object[0], "Invite not found."));

        if (invite.isExpired()) {
            throw new ConflictException("error.team.invite.expired", new Object[]{invite.getId()}, "This invite link has expired. inviteId=" + invite.getId());
        }
        log.debug("Invite locked for acceptance for teamId {} and role {}", invite.getTeam().getId(), invite.getInvitedRole());
        return invite;
    }

    private String buildInviteLink(String token) {
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/team-invites/")
                    .path(token)
                    .toUriString();
        } catch (IllegalStateException ex) {
            return "/api/team-invites/" + token;
        }
    }

    private TeamInviteResponse toResponse(TeamInvite invite) {
        String inviteLink = buildInviteLink(invite.getToken());

        int remainingUses = Math.max(invite.getMaxUses() - invite.getUsedCount(), 0);

        return new TeamInviteResponse(
                invite.getId(),
                invite.getTeam().getId(),
                invite.getTeam().getName(),
                invite.getInvitedRole(),
                null,
                inviteLink,
                invite.getMaxUses(),
                invite.getUsedCount(),
                remainingUses,
                invite.getExpiresAt(),
                invite.isExpired(),
                invite.getPlayerProfile() != null ? invite.getPlayerProfile().getFirstName() : null,
                invite.getPlayerProfile() != null ? invite.getPlayerProfile().getLastName() : null,
                resolvedKind(invite)
        );
    }
}
