package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.InviteKind;
import com.example.footballanalysis.model.db.PlayerParent;
import com.example.footballanalysis.model.db.PlayerParentId;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.TeamInvite;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;

import com.example.footballanalysis.model.requests.AcceptInviteRequest;
import com.example.footballanalysis.model.responses.InviteTokenResponse;
import com.example.footballanalysis.model.responses.TeamInviteResponse;
import com.example.footballanalysis.repository.PlayerParentRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.TeamInviteRepository;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import com.example.footballanalysis.repository.TeamRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final TeamMembershipQuery teamMembershipQuery;
    private final ExtraHatService extraHatService;

    /**
    * Meghívó token generálása egy adott csapathoz egy bizonyos szerepkörre.
     *
     * @param teamId A csapat egyedi azonosítója, amelyhez a meghívó készül.
     * @param actor A hívást kezdeményező hitelesített felhasználó (User objektum).
     * @param requestedRole A meghívott felhasználó kért szerepköre (alapértelmezetten PLAYER).
     * @return Az elkészített meghívó tokent tartalmazó válasz.
     */
    @Transactional
    public InviteTokenResponse generateInviteToken(UUID teamId, User actor, UserRole requestedRole, UserRole view) {
        InviteKind resolvedKind = InviteKind.fromRole(requestedRole);
        if (resolvedKind == null) {
            throw new BadRequestException("error.team.invite.role_required", new Object[0], "Invite role must be provided.");
        }

        Set<String> actorRoles = extractActorRoles(actor);
        TeamInvite invite = createInviteEntity(teamId, actor, actorRoles, resolvedKind, view, LocalDateTime.now().plusHours(GENERATION_EXPIRATION_HOURS));
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
    private TeamInvite createInviteEntity(UUID teamId, User actor, Set<String> actorRoles, InviteKind kind, UserRole view, LocalDateTime expiresAt) {
        UserRole invitedRole = kind.invitedRole();
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found: " + teamId));

        if (view == null || !hasRole(actorRoles, view)) {
            throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied.");
        }

        // Csak a kérésben kapott nézet sapkája számít, a token többi sapkája nem.
        UUID actorId = actor.getId();
        switch (view) {
            case ADMIN -> {
            }
            case COACH -> {
                if (!isCoachOnTeam(actorId, teamId)) {
                    throw new ConflictException(
                            "error.team.invite.not_authorized",
                            new Object[]{teamId, actorId},
                            "Requester is not assigned to this team.");
                }
            }
            case CLUB_ADMIN -> {
                UUID clubId = team.getClub() != null ? team.getClub().getId() : null;
                if (!userAccessService.isClubAdmin(actor, clubId)) {
                    throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied.");
                }
            }
            case PLAYER -> {
                if (!isPlayerOnTeam(actorId, teamId)) {
                    throw new ConflictException(
                            "error.team.invite.not_authorized",
                            new Object[]{teamId, actorId},
                            "Requester is not assigned to this team.");
                }
                ensurePlayerCanOnlyInviteFans(invitedRole);
            }
            case PARENT -> {
                if (!isParentOnTeam(actorId, teamId)) {
                    throw new ConflictException(
                            "error.team.invite.not_authorized",
                            new Object[]{teamId, actorId},
                            "Requester is not assigned to this team.");
                }
                ensurePlayerCanOnlyInviteFans(invitedRole);
            }
            default -> throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied.");
        }

        // Biztonsági okokból adminba nem hívhatunk meg senkit! Ezt kizárjuk.
        if (invitedRole == UserRole.ADMIN) {
            throw new BadRequestException("error.team.invite.invalid_role", new Object[]{invitedRole}, "Invites cannot be created for ADMIN users.");
        }

        TeamInvite invite = new TeamInvite();
        invite.setTeam(team);
        invite.setCreatedByUserId(actor.getId());
        invite.setCreatedByUserRole(view);
        invite.setInvitedRole(invitedRole);
        invite.setInviteKind(kind);
        invite.setToken(UUID.randomUUID().toString());
        invite.setMaxUses(GENERATION_MAX_USES);
        invite.setUsedCount(0);
        invite.setExpiresAt(expiresAt);
        return invite;
    }

    private static boolean hasRole(Set<String> actorRoles, UserRole role) {
        return actorRoles.contains(role.name());
    }

    private boolean isCoachOnTeam(UUID coachId, UUID teamId) {
        return coachId != null && teamMembershipQuery.isCoachOnTeam(coachId, teamId);
    }

    private boolean isPlayerOnTeam(UUID playerId, UUID teamId) {
        return playerId != null && playerProfileRepository.existsByLinkedUser_IdAndTeam_Id(playerId, teamId);
    }

    private boolean isParentOnTeam(UUID parentId, UUID teamId) {
        return parentId != null
                && playerParentRepository.existsByUser_IdAndPlayerProfile_Team_IdIn(parentId, List.of(teamId));
    }

    // Megtiltja, hogy játékos vagy szülő FAN szerepkörtől eltérő meghívót hozzon létre.
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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            auth.getAuthorities().forEach(granted -> {
                UserRole hat = parseAuthority(granted.getAuthority());
                if (hat != null) {
                    roles.add(hat.name());
                }
            });
        }
        if (roles.isEmpty() && actor != null && actor.getRole() != null) {
            roles.add(actor.getRole().name());
        }
        return roles;
    }

    // A Spring Security jogosultságnevét a megfelelő felhasználói szerepkörré alakítja.
    private static UserRole parseAuthority(String authority) {
        if (authority == null || authority.isBlank()) {
            return null;
        }
        String name = authority.startsWith("ROLE_") ? authority.substring(5) : authority;
        return switch (name.toUpperCase()) {
            case "ADMIN" -> UserRole.ADMIN;
            case "COACH" -> UserRole.COACH;
            case "PLAYER" -> UserRole.PLAYER;
            case "PARENT" -> UserRole.PARENT;
            case "CLUB_ADMIN" -> UserRole.CLUB_ADMIN;
            case "FAN" -> UserRole.FAN;
            default -> null;
        };
    }

    // Kiválasztja az aktor naplózáshoz használt elsődleges szerepkörét.
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
            return toResponse(findReadableInvite(token));
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
        TeamInvite invite = found.get();
        InviteKind kind = resolvedKind(invite);
        extraHatService.requireKindAllowed(actor, kind);

        boolean registrationAlreadyConsumed = invite.claimPendingRegistration(actor.getId());
        if (!registrationAlreadyConsumed && invite.isExpired()) {
            throw new ConflictException("error.team.invite.expired", new Object[]{invite.getId()}, "This invite link has expired. inviteId=" + invite.getId());
        }
        if (!registrationAlreadyConsumed && invite.isExhausted()) {
            throw new ConflictException("error.team.invite.exhausted", new Object[]{invite.getId()}, "This invite link has reached its maximum number of uses. inviteId=" + invite.getId());
        }

        fulfillInvite(invite, actor, details);
        extraHatService.ensureHat(actor, kind.invitedRole());

        UUID userId = actor.getId();
        if (!registrationAlreadyConsumed) {
            invite.setUsedCount(invite.getUsedCount() + 1);
        }
        auditEventService.record(
            "TEAM_INVITE_ACCEPTED",
            userId,
            actor.getRole() != null ? actor.getRole().name() : null,
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
        boolean admin = userAccessService.isAdmin(actor);
        boolean clubAdmin = userAccessService.isClubAdminOfProfile(actor, profileId);
        boolean parentOfChild = playerParentRepository.existsById(
                new com.example.footballanalysis.model.db.PlayerParentId(actor.getId(), profileId));
        if (!admin && !clubAdmin && !parentOfChild) {
            throw new ForbiddenException("error.auth.forbidden", new Object[0], "Only a parent of this player can create a child login.");
        }
        List<Team> teams = playerProfileRepository.findTeamsByProfileId(profileId);
        boolean onTeam = teams != null && !teams.isEmpty();
        boolean hasParent = playerParentRepository.existsByPlayerProfile_Id(profileId);
        if (!onTeam && !hasParent) {
            throw new ConflictException("error.team.invite.profile_not_on_team", new Object[]{profileId}, "Player profile is not on a team.");
        }
        if (profile.getLinkedUser() != null) {
            throw new ConflictException("error.player_profile.login_exists", new Object[0], "This player profile already has a login.");
        }
        playerParentService.rejectClubAdminIfProfileOutsideClubs(profile, actor);
        return profile;
    }

    @Transactional
    public InviteTokenResponse createChildLoginInvite(UUID profileId, User actor) {
        PlayerProfile profile = requireEligibleChildLogin(profileId, actor);
        List<Team> teams = playerProfileRepository.findTeamsByProfileId(profile.getId());
        if (teams == null || teams.isEmpty()) {
            throw new ConflictException(
                    "error.team.invite.profile_not_on_team",
                    new Object[]{profileId},
                    "Player profile is not on a team.");
        }

        TeamInvite invite = new TeamInvite();
        invite.setTeam(chooseChildLoginTeam(teams, actor));
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
        // A player who already has a profile only joins the team. The adult date-of-birth check
        // stays on validateAcceptance, which this branch does not call.
        if (resolvedKind(invite) == InviteKind.PLAYER_SELF && hasLinkedPlayerProfile(user)) {
            teamService.addPlayerToTeam(invite.getTeam().getId(), user.getId(), user, false);
            return;
        }
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
            case CHILD_LOGIN -> playerParentService.attachChildLogin(user, invite.getPlayerProfile(), invite.getId());
            case COACH -> teamService.addCoachToTeam(invite.getTeam().getId(), user.getId(), user, false);
            case FAN -> teamService.addFanToTeam(invite.getTeam().getId(), user.getId(), user, false);
            case CLUB_ADMIN -> throw new BadRequestException(
                    "error.team.invite.invalid_role", new Object[0], "Club admin invites are not team invites.");
        }
    }

    // A szülőhöz már kapcsolt gyermek profilját hozzáadja a meghívó csapatához.
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
        User linked = profile.getLinkedUser();
        if (linked != null && linked.getId() != null) {
            extraHatService.requireTeamRoleFree(linked.getId(), invite.getTeam().getId(), UserRole.PLAYER);
        }
        List<PlayerParent> parents = playerParentRepository.findByPlayerProfile_Id(profileId);
        if (parents != null) {
            for (PlayerParent link : parents) {
                User parentUser = link.getUser();
                if (parentUser != null
                        && parentUser.getId() != null
                        && playerProfileRepository.existsByLinkedUser_IdAndTeam_Id(
                                parentUser.getId(), invite.getTeam().getId())) {
                    throw new ConflictException(
                            "error.team.role_conflict",
                            new Object[0],
                            "This account already has another role on this team.");
                }
            }
        }
        teamService.addProfileToTeam(profile, invite.getTeam());
        if (linked != null && linked.getId() != null) {
            extraHatService.grantHat(linked.getId(), UserRole.PLAYER);
        }
    }

    // Ellenőrzi, hogy az új gyermek profiljához megadták-e a kötelező adatokat.
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

    // A már létező játékosprofilnál a meghívó csak a csapathoz adja a fiókot, új születési dátum nélkül.
    private boolean hasLinkedPlayerProfile(User user) {
        if (user == null || user.getId() == null) {
            return false;
        }
        return playerProfileRepository.existsByLinkedUser_Id(user.getId());
    }

    // Ellenőrzi, hogy a játékos önálló fiókhoz betöltötte-e a 16. életévét.
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

    // Meghatározza a meghívó típusát, szükség esetén a meghívott szerepköréből.
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
        return findReadableInvite(token);
    }

    private TeamInvite findReadableInvite(String token) {
        TeamInvite invite = teamInviteRepository.findByToken(token)
                .orElseThrow(() -> new NotFoundException("error.team.invite.not_found", new Object[0], "Invite not found."));

        if (invite.isExpired() && !invite.hasPendingRegistration()) {
            throw new ConflictException("error.team.invite.expired", new Object[]{invite.getId()}, "This invite link has expired. inviteId=" + invite.getId());
        }
        log.debug("Invite validated for teamId {} and role {}", invite.getTeam().getId(), invite.getInvitedRole());
        return invite;
    }

    private Team chooseChildLoginTeam(List<Team> teams, User actor) {
        for (Team team : teams) {
            if (team == null || team.getClub() == null || team.getClub().getId() == null) {
                continue;
            }
            if (userAccessService.isClubAdmin(actor, team.getClub().getId())) {
                return team;
            }
        }
        UUID actorId = actor != null ? actor.getId() : null;
        for (Team team : teams) {
            if (team != null && isCoachOnTeam(actorId, team.getId())) {
                return team;
            }
        }
        return teams.get(0);
    }

    // Összeállítja a meghívó teljes URL-jét, vagy visszaad egy relatív útvonalat.
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

    // A meghívó entitásból kiszámítja és előállítja az API válaszát.
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
