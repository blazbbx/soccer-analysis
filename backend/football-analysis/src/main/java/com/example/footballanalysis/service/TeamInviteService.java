package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.TeamInvite;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Fan;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;

import com.example.footballanalysis.model.responses.InviteTokenResponse;
import com.example.footballanalysis.model.responses.TeamInviteResponse;
import com.example.footballanalysis.repository.CoachRepository;
import com.example.footballanalysis.repository.FanRepository;
import com.example.footballanalysis.repository.PlayerRepository;
import com.example.footballanalysis.repository.UserRepository;
import com.example.footballanalysis.repository.TeamInviteRepository;
import com.example.footballanalysis.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
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
    private final CoachRepository coachRepository;
    private final FanRepository fanRepository;
    private final PlayerRepository playerRepository;
    private final UserRepository userRepository;
    private final TeamInviteRepository teamInviteRepository;
    private final TeamService teamService;
    private final AuditEventService auditEventService;

    @Value("${idp.keycloak.client-id:}")
    private String clientId;

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
        if (requestedRole == null) {
            throw new BadRequestException("error.team.invite.role_required", new Object[0], "Invite role must be provided.");
        }

        Set<String> actorRoles = extractActorRoles(actor);
        TeamInvite invite = createInviteEntity(teamId, actor, actorRoles, requestedRole, LocalDateTime.now().plusHours(GENERATION_EXPIRATION_HOURS));
        teamInviteRepository.save(invite);
        auditEventService.record(
            "TEAM_INVITE_GENERATED",
            invite.getCreatedByUserId(),
            invite.getCreatedByUserRole() != null ? invite.getCreatedByUserRole().name() : null,
            "TEAM",
            teamId.toString(),
            "requestedRole=" + requestedRole + ", maxUses=" + invite.getMaxUses() + ", expiresAt=" + invite.getExpiresAt()
        );
        log.atInfo()
                .setMessage("Generated new team invite link for teamId={}, requestedRole={}, createdBy={}, createdByRole={}")
                .addArgument(teamId)
                .addArgument(requestedRole)
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
    private TeamInvite createInviteEntity(UUID teamId, User actor, Set<String> actorRoles, UserRole invitedRole, LocalDateTime expiresAt) {
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
            } else {
                throw new UnauthorizedException("error.auth.forbidden", new Object[0], "Access denied.");
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
        invite.setToken(UUID.randomUUID().toString()); // Itt kapja meg az egyedi, titkos azonosítóját
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
        if (playerId == null || team.getPlayers().stream().map(Player::getId).noneMatch(playerId::equals)) {
            throw new ConflictException("error.team.invite.not_authorized", new Object[]{teamId, player.getId()}, "Requester is not assigned to this team.");
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
        if (actorRoles.contains(UserRole.FAN.name())) {
            return UserRole.FAN;
        }

        throw new BadRequestException("error.auth.role_missing", new Object[0], "Authenticated token does not contain a supported role.");
    }

    private boolean isPlayerActor(Set<String> actorRoles) {
        return actorRoles.contains(UserRole.PLAYER.name())
                && !actorRoles.contains(UserRole.COACH.name())
                && !actorRoles.contains(UserRole.ADMIN.name());
    }

    private Set<String> extractRoles(Jwt jwt) {
        Set<String> roles = new LinkedHashSet<>();
        roles.addAll(extractRolesFromClaim(jwt.getClaim("realm_access")));

        Object resourceAccessClaim = jwt.getClaim("resource_access");
        if (resourceAccessClaim instanceof Map<?, ?> resourceAccess) {
            if (clientId != null && !clientId.isBlank()) {
                roles.addAll(extractRolesFromClaim(resourceAccess.get(clientId)));
            }
            for (Object clientAccess : resourceAccess.values()) {
                roles.addAll(extractRolesFromClaim(clientAccess));
            }
        }

        Set<String> normalizedRoles = new LinkedHashSet<>();
        for (String role : roles) {
            if (role != null && !role.isBlank()) {
                normalizedRoles.add(role.toUpperCase());
            }
        }
        
        return normalizedRoles;
    }

    /**
     * Kinyeri egy adott Keycloak claim node-ból (pl realm_access fában lévő dolgokból) a 'roles' tömb tartalmát.
     *
     * @param claim A vizsgálandó JWT claim objektum.
     * @return A kiolvasott szerepkörök halmaza, vagy üres halmaz ha nem található.
     */
    private Set<String> extractRolesFromClaim(Object claim) {
        if (!(claim instanceof Map<?, ?> rolesContainer)) {
            return Set.of();
        }

        Object rolesObject = rolesContainer.get("roles");
        if (!(rolesObject instanceof Collection<?> roles)) {
            return Set.of();
        }

        Set<String> extractedRoles = new LinkedHashSet<>();
        for (Object role : roles) {
            if (role instanceof String roleName) {
                extractedRoles.add(roleName);
            }
        }
        
        return extractedRoles;
    }

    /**
     * Lekérdezi egy aktív meghívó adatait a megadott token alapján.
     *
     * @param token A meghívó egyedi, token formátumú azonosítója.
     * @return A meghívó részletes adatait tartalmazó válasz DTO.
     */
    @Transactional
    public TeamInviteResponse getInvite(String token) {
        return toResponse(findActiveInvite(token));
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
        if (actor == null) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required to accept an invite.");
        }
        TeamInvite invite = findActiveInviteForUpdate(token);

        if (invite.isExhausted()) {
            throw new ConflictException("error.team.invite.exhausted", new Object[]{invite.getId()}, "This invite link has reached its maximum number of uses. inviteId=" + invite.getId());
        }

        UUID userId = actor.getId();
        switch (invite.getInvitedRole()) {
            case PLAYER -> teamService.addPlayerToTeam(invite.getTeam().getId(), userId, actor);
            case COACH -> teamService.addCoachToTeam(invite.getTeam().getId(), userId, actor);
            case FAN -> teamService.addFanToTeam(invite.getTeam().getId(), userId, actor);
            default -> throw new BadRequestException("error.team.invite.invalid_role", new Object[]{invite.getInvitedRole()}, "Unsupported invite role.");
        }

        invite.setUsedCount(invite.getUsedCount() + 1);
        auditEventService.record(
            "TEAM_INVITE_ACCEPTED",
            userId,
            invite.getInvitedRole() != null ? invite.getInvitedRole().name() : null,
            "TEAM",
            invite.getTeam().getId().toString(),
            "flow=direct_accept, usedCount=" + invite.getUsedCount() + "/" + invite.getMaxUses()
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
                .addKeyValue("team_id", invite.getTeam().getId())
                .addKeyValue("assigned_role", invite.getInvitedRole())
                .addKeyValue("used_count", invite.getUsedCount())
                .addKeyValue("max_uses", invite.getMaxUses())
                .log();
        return toResponse(teamInviteRepository.save(invite));
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

    /**
     * Kinyeri a hitelesített edzőt a JWT-ből.
     * A szolgáltatás először a Keycloak subject alapján keres, majd tartalékként az email címet használja.
     *
     * @param jwt A felhasználó JWT tokenje.
     * @return A megtalált hitelesített edző (Coach).
     * @throws NotFoundException Ha a megadott adatok alapján nem található edző.
     */
    private Coach resolveCoach(Jwt jwt) {
        String email = resolveEmail(jwt);
        String subject = resolveSubject(jwt).orElse(null);

        return findCoachBySubject(subject)
                .or(() -> coachRepository.findByEmail(email))
                .orElseThrow(() -> new NotFoundException("error.coach.not_found", new Object[]{email}, "Coach not found for authenticated user: " + email));
    }

    private Player resolvePlayer(Jwt jwt) {
        String email = resolveEmail(jwt);
        String subject = resolveSubject(jwt).orElse(null);

        return findPlayerBySubject(subject)
                .or(() -> playerRepository.findByEmail(email))
                .orElseThrow(() -> new NotFoundException("error.player.not_found", new Object[]{email}, "Player not found for authenticated user: " + email));
    }

    private User resolveCreatorUser(Jwt jwt) {
        String email = resolveEmail(jwt);
        String subject = resolveSubject(jwt).orElse(null);

        return findUserBySubject(subject)
                .or(() -> userRepository.findByEmail(email))
                .orElseThrow(() -> new NotFoundException("error.user.not_found", new Object[]{email}, "User not found for authenticated user: " + email));
    }

    /**
     * Kinyeri az aktuális felhasználó azonosítóját a JWT adatok alapján.
     * Az azonosítás sorrendje: Keycloak subject, majd email lookup.
     *
     * @param jwt A felhasználó JWT tokenje.
     * @param role A szerepkör, amely meghatározza, melyik felhasználói táblában keressünk.
     * @return A megtalált profil egyedi azonosítója.
     */
    private UUID resolveInviteeId(Jwt jwt, UserRole role) {
        requireJwt(jwt);
        Optional<String> subject = resolveSubject(jwt);
        String email = resolveEmail(jwt);

        return switch (role) {
            case PLAYER -> resolvePlayerId(subject.orElse(null), email);
            case COACH -> resolveCoachId(subject.orElse(null), email);
            case FAN -> resolveFanId(subject.orElse(null), email);
            default -> throw new BadRequestException("error.team.invite.invalid_role", new Object[]{role}, "Unsupported invite role.");
        };
    }

    /**
     * Kinyeri a felhasználó keycloak azonosítóját a JWT-ből.
     *
     * @param jwt A vizsgálandó JWT token.
     * @return A felhasználó egyedi (subject) azonosítója, vagy üres érték.
     */
    private Optional<String> resolveSubject(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(subject);
    }

    private Optional<Coach> findCoachBySubject(String subject) {
        return resolveSubjectAsUuid(subject).flatMap(coachRepository::findById);
    }

    private Optional<Player> findPlayerBySubject(String subject) {
        return resolveSubjectAsUuid(subject).flatMap(playerRepository::findById);
    }

    private Optional<Fan> findFanBySubject(String subject) {
        return resolveSubjectAsUuid(subject).flatMap(fanRepository::findById);
    }

    private Optional<User> findUserBySubject(String subject) {
        return resolveSubjectAsUuid(subject).flatMap(userRepository::findById);
    }

    private Optional<UUID> resolveSubjectAsUuid(String subject) {
        if (subject == null || subject.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(subject));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private UUID resolvePlayerId(String subject, String email) {
        return findPlayerBySubject(subject)
                .or(() -> playerRepository.findByEmail(email))
                .map(Player::getId)
                .orElseThrow(() -> new NotFoundException("error.player.not_found", new Object[]{email}, "Player not found for authenticated user: " + email));
    }

    private UUID resolveCoachId(String subject, String email) {
        return findCoachBySubject(subject)
                .or(() -> coachRepository.findByEmail(email))
                .map(Coach::getId)
                .orElseThrow(() -> new NotFoundException("error.coach.not_found", new Object[]{email}, "Coach not found for authenticated user: " + email));
    }

    private UUID resolveFanId(String subject, String email) {
        return findFanBySubject(subject)
                .or(() -> fanRepository.findByEmail(email))
                .map(Fan::getId)
                .orElseThrow(() -> new NotFoundException("error.fan.not_found", new Object[]{email}, "Fan not found for authenticated user: " + email));
    }

    /**
     * Kinyeri a felhasználó email címét a JWT-ből.
     * Ha az "email" claim hiányzik, a "preferred_username" mezőben próbálja megtalálni.
     *
     * @param jwt A hitelesített felhasználó JWT tokenje.
     * @return A felhasználó email címe.
     * @throws UnauthorizedException Ha a token hiányzik.
     * @throws BadRequestException Ha az email nem található a tokenben.
     */
    private String resolveEmail(Jwt jwt) {
        if (jwt == null) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required to access this resource.");
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            email = jwt.getClaimAsString("preferred_username");
        }
        if (email == null || email.isBlank()) {
            throw new BadRequestException("error.auth.email_missing", new Object[0], "Authenticated token does not contain an email.");
        }
        return email;
    }

    private void requireJwt(Jwt jwt) {
        if (jwt == null) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required to access this resource.");
        }
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
                invite.getToken(),
                inviteLink,
                invite.getMaxUses(),
                invite.getUsedCount(),
                remainingUses,
                invite.getExpiresAt(),
                invite.isExpired()
        );
    }
}
