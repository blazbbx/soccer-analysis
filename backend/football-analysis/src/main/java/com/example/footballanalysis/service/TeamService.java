package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Fan;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.requests.CreateTeamRequest;
import com.example.footballanalysis.model.requests.UpdateTeamRequest;
import com.example.footballanalysis.model.responses.TeamResponse;
import com.example.footballanalysis.repository.ClipRepository;
import com.example.footballanalysis.repository.CoachRepository;
import com.example.footballanalysis.repository.FanRepository;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.MatchSquadMemberRepository;
import com.example.footballanalysis.repository.PlayerRepository;
import com.example.footballanalysis.repository.TeamInviteRepository;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamService {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final CoachRepository coachRepository;
    private final FanRepository fanRepository;
    private final TeamInviteRepository teamInviteRepository;
    private final MatchRepository matchRepository;
    private final ClipRepository clipRepository;
    private final MatchSquadMemberRepository matchSquadMemberRepository;
    private final UserRepository userRepository;
    private final MinioObjectCleanupService minioObjectCleanupService;
    private final AuditEventService auditEventService;
    private final UserAccessService userAccessService;

    // ── Csapat lekérdezések ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TeamResponse> getAllTeams() {
        log.debug("Fetching all teams");
        List<Team> teamsFromDb = teamRepository.findAll();
        if (teamsFromDb.isEmpty()) {
            log.debug("Found 0 teams");
            return List.of();
        }

        List<TeamResponse> teams = teamsFromDb.stream().map(this::toResponse).toList();
        log.debug("Found {} teams", teams.size());
        return teams;
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> getMyTeams(Jwt jwt) {
        UserRole role = resolveRole(jwt);

        List<Team> teams = switch (role) {
            case ADMIN -> teamRepository.findAll();
            case COACH -> resolveCoach(jwt).getTeams().stream().toList();
            case PLAYER -> resolvePlayer(jwt).getTeams().stream().toList();
            case FAN -> resolveFan(jwt).getTeams().stream().toList();
        };

        return teams.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TeamResponse getTeam(UUID id) {
        log.debug("Fetching team with ID: {}", id);
        return toResponse(teamRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{id}, "Team not found: " + id)));
    }

    // ── Csapat módosítások (Create, Update, Delete) ───────────────────────────

    @Transactional
    public TeamResponse createTeam(CreateTeamRequest request) {
        return createTeam(request, null);
    }

    @Transactional
    public TeamResponse createTeam(CreateTeamRequest request, Jwt jwt) {
        String name = request.name().trim();
        if (name.isBlank()) {
            throw new BadRequestException("validation.team.name.required", new Object[0], "Team name is required.");
        }
        if (teamRepository.existsByName(name)) {
            throw new ConflictException("error.team.name.conflict", new Object[]{name}, "A team with this name already exists: " + name);
        }

        Team team = new Team();
        team.setName(name);
        team.setShortName(request.shortName());
        team.setLogoUrl(request.logoUrl());

        Team savedTeam = teamRepository.save(team);
        
        if (jwt != null && resolveRole(jwt) == UserRole.COACH) {
            Coach coach = resolveCoach(jwt);
            coach.addTeam(savedTeam);
            coachRepository.save(coach);
        }

        auditEventService.record(
            "TEAM_CREATED",
            jwt != null ? resolveCreatorUserId(jwt) : null,
            jwt != null ? resolveRole(jwt).name() : null,
            "TEAM",
            savedTeam.getId().toString(),
            "name=" + savedTeam.getName() + ", shortName=" + savedTeam.getShortName()
        );

        log.atInfo()
                .setMessage("Created new team with ID: {} and name: {}")
                .addArgument(savedTeam.getId())
                .addArgument(savedTeam.getName())
                .addKeyValue("teamId", savedTeam.getId())
                .addKeyValue("teamName", savedTeam.getName())
                .log();

        return toResponse(savedTeam);
    }

    @Transactional
    public TeamResponse updateTeam(UUID id, UpdateTeamRequest request, Jwt jwt) {
        User actor = resolveCurrentUser(jwt);
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{id}, "Team not found: " + id));

        userAccessService.hasTeamAccess(actor, List.of(id));

        String name = request.name().trim();
        if (name.isBlank()) {
            throw new BadRequestException("validation.team.name.required", new Object[0], "Team name is required.");
        }
        if (teamRepository.existsByNameAndIdNot(name, id)) {
            throw new ConflictException("error.team.name.conflict", new Object[]{name}, "Another team with this name already exists: " + name);
        }

        team.setName(name);
        team.setShortName(request.shortName());
        team.setLogoUrl(request.logoUrl());
        
        log.atInfo()
                .setMessage("Updating team with ID: {}. New name: {}, shortName: {}, logoUrl: {}")
                .addArgument(team.getId())
                .addArgument(team.getName())
                .addArgument(team.getShortName())
                .addArgument(team.getLogoUrl())
                .addKeyValue("teamId", team.getId())
                .addKeyValue("teamName", team.getName())
                .log();
                
        return toResponse(teamRepository.save(team));
    }

    @Transactional
    public void deleteTeam(UUID id, Jwt jwt) {
        log.info("Attempting to delete team with ID: {}", id);
        User actor = resolveCurrentUser(jwt);
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{id}, "Team not found: " + id));

        userAccessService.hasTeamAccess(actor, List.of(id));

        log.debug("Deleting related matching data and clips for team: {}", id);
        List<Match> matches = matchRepository.findAllByHomeTeam_IdOrAwayTeam_Id(id, id);
        List<UUID> matchIds = matches.stream().map(Match::getId).toList();
        List<Clip> clips = matchIds.isEmpty() ? List.of() : clipRepository.findAllByMatch_IdIn(matchIds);

        clearTeamMemberships(team);
        teamInviteRepository.deleteAllByTeam_Id(id);
        matchSquadMemberRepository.deleteAllByTeam_Id(id);

        if (!matchIds.isEmpty()) {
            clipRepository.deleteAllByMatch_IdIn(matchIds);
            matchRepository.deleteAll(matches);
        }

        teamRepository.delete(team);
        teamRepository.flush();

        minioObjectCleanupService.deleteMatchArtifactsForTeamDeletion(matches, clips);
        
        auditEventService.record(
            "TEAM_DELETED",
            actor != null ? actor.getId() : null,
            actor != null ? actor.getRole().name() : null, // Mivel eltüntettük getUserRole-t, ez itt a DB-s role lehet
            "TEAM",
            id.toString(),
            "matchIds=" + formatUuidList(matchIds) + ", clipIds=" + formatUuidList(clips.stream().map(Clip::getId).toList())
        );

        log.info("Team successfully deleted: teamId={}, matchesDeleted={}, clipsDeleted={}", id, matches.size(), clips.size());
    }

    private void clearTeamMemberships(Team team) {
        playerRepository.removeAllPlayersFromTeam(team.getId());
        coachRepository.removeAllCoachesFromTeam(team.getId());
        fanRepository.removeAllFansFromTeam(team.getId());
        team.getPlayers().clear();
        team.getCoaches().clear();
        log.debug("Cleared team memberships for team: {}", team.getId());
    }

    // ── Tagok kezelése (Player, Coach, Fan) ───────────────────────────────────

    @Transactional
    public void addPlayerToTeam(UUID teamId, UUID playerId) { addPlayerToTeam(teamId, playerId, null); }

    @Transactional
    public void addPlayerToTeam(UUID teamId, UUID playerId, Jwt jwt) {
        User actor = resolveCurrentUser(jwt);
        Team team = teamRepository.findById(teamId).orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found."));
        if (jwt != null) userAccessService.hasTeamAccess(actor, List.of(teamId));
        
        Player player = playerRepository.findById(playerId).orElseThrow(() -> new NotFoundException("error.player.not_found", new Object[]{playerId}, "Player not found."));
        if (team.getPlayers().contains(player)) throw new ConflictException("error.team.player.already_member", new Object[0], "Player already in team.");

        player.addTeam(team);
        playerRepository.save(player);
        log.info("Added player {} to team {}", playerId, teamId);
    }

    @Transactional
    public void removePlayerFromTeam(UUID teamId, UUID playerId) { removePlayerFromTeam(teamId, playerId, null); }

    @Transactional
    public void removePlayerFromTeam(UUID teamId, UUID playerId, Jwt jwt) {
        User actor = resolveCurrentUser(jwt);
        Team team = teamRepository.findById(teamId).orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found."));
        if (jwt != null) userAccessService.hasTeamAccess(actor, List.of(teamId));
        
        Player player = playerRepository.findById(playerId).orElseThrow(() -> new NotFoundException("error.player.not_found", new Object[]{playerId}, "Player not found."));
        if (!team.getPlayers().contains(player)) throw new ConflictException("error.team.player.not_member", new Object[0], "Player not in team.");

        player.removeTeam(team);
        playerRepository.save(player);
        log.info("Removed player {} from team {}", playerId, teamId);
    }

    @Transactional
    public void addCoachToTeam(UUID teamId, UUID coachId) { addCoachToTeam(teamId, coachId, null); }

    @Transactional
    public void addCoachToTeam(UUID teamId, UUID coachId, Jwt jwt) {
        User actor = resolveCurrentUser(jwt);
        Team team = teamRepository.findById(teamId).orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found."));
        if (jwt != null) userAccessService.hasTeamAccess(actor, List.of(teamId));
        
        Coach coach = coachRepository.findById(coachId).orElseThrow(() -> new NotFoundException("error.coach.not_found", new Object[]{coachId}, "Coach not found."));
        if (team.getCoaches().contains(coach)) throw new ConflictException("error.team.coach.already_member", new Object[0], "Coach already in team.");

        coach.addTeam(team);
        coachRepository.save(coach);
        log.info("Added coach {} to team {}", coachId, teamId);
    }

    @Transactional
    public void removeCoachFromTeam(UUID teamId, UUID coachId) { removeCoachFromTeam(teamId, coachId, null); }

    @Transactional
    public void removeCoachFromTeam(UUID teamId, UUID coachId, Jwt jwt) {
        User actor = resolveCurrentUser(jwt);
        Team team = teamRepository.findById(teamId).orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found."));
        if (jwt != null) userAccessService.hasTeamAccess(actor, List.of(teamId));
        
        Coach coach = coachRepository.findById(coachId).orElseThrow(() -> new NotFoundException("error.coach.not_found", new Object[]{coachId}, "Coach not found."));
        if (!team.getCoaches().contains(coach)) throw new ConflictException("error.team.coach.not_member", new Object[0], "Coach not in team.");

        coach.removeTeam(team);
        coachRepository.save(coach);
        log.info("Removed coach {} from team {}", coachId, teamId);
    }

    @Transactional
    public void addFanToTeam(UUID teamId, UUID fanId) { addFanToTeam(teamId, fanId, null); }

    @Transactional
    public void addFanToTeam(UUID teamId, UUID fanId, Jwt jwt) {
        User actor = resolveCurrentUser(jwt);
        Team team = teamRepository.findById(teamId).orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found."));
        if (jwt != null) userAccessService.hasTeamAccess(actor, List.of(teamId));
        
        Fan fan = fanRepository.findById(fanId).orElseThrow(() -> new NotFoundException("error.fan.not_found", new Object[]{fanId}, "Fan not found."));
        if (fan.getTeams().contains(team)) throw new ConflictException("error.team.fan.already_following", new Object[0], "Fan already following team.");

        fan.addTeam(team);
        fanRepository.save(fan);
        log.info("Added fan {} to team {}", fanId, teamId);
    }

    @Transactional
    public void removeFanFromTeam(UUID teamId, UUID fanId) { removeFanFromTeam(teamId, fanId, null); }

    @Transactional
    public void removeFanFromTeam(UUID teamId, UUID fanId, Jwt jwt) {
        User actor = resolveCurrentUser(jwt);
        Team team = teamRepository.findById(teamId).orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found."));
        if (jwt != null) userAccessService.hasTeamAccess(actor, List.of(teamId));
        
        Fan fan = fanRepository.findById(fanId).orElseThrow(() -> new NotFoundException("error.fan.not_found", new Object[]{fanId}, "Fan not found."));
        if (!fan.getTeams().contains(team)) throw new ConflictException("error.team.fan.not_following", new Object[0], "Fan not following team.");

        fan.removeTeam(team);
        fanRepository.save(fan);
        log.info("Removed fan {} from team {}", fanId, teamId);
    }

    // ── Security / JWT Feloldó Segédmetódusok ─────────────────────────────────

    private UserRole resolveRole(Jwt jwt) {
        if (jwt == null) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "No JWT token provided.");
        }
        
        Set<String> roles = extractRoles(jwt);
        
        return roles.stream()
                .map(this::safeUserRole)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("error.auth.role_missing", new Object[0], "Authenticated token does not contain a supported role."));
    }

    private UserRole safeUserRole(String roleName) {
        try {
            return UserRole.valueOf(roleName);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Coach resolveCoach(Jwt jwt) {
        String email = resolveEmail(jwt);
        String subject = resolveSubject(jwt).orElse(null);
        return findCoachBySubject(subject)
                .or(() -> coachRepository.findByEmail(email))
                .orElseThrow(() -> new NotFoundException("error.coach.not_found", new Object[]{email}, "Coach not found for user: " + email));
    }

    private Player resolvePlayer(Jwt jwt) {
        String email = resolveEmail(jwt);
        String subject = resolveSubject(jwt).orElse(null);
        return findPlayerBySubject(subject)
                .or(() -> playerRepository.findByEmail(email))
                .orElseThrow(() -> new NotFoundException("error.player.not_found", new Object[]{email}, "Player not found for user: " + email));
    }

    private Fan resolveFan(Jwt jwt) {
        String email = resolveEmail(jwt);
        String subject = resolveSubject(jwt).orElse(null);
        return findFanBySubject(subject)
                .or(() -> fanRepository.findByEmail(email))
                .orElseThrow(() -> new NotFoundException("error.fan.not_found", new Object[]{email}, "Fan not found for user: " + email));
    }

    private User resolveCurrentUser(Jwt jwt) {
        if (jwt == null) return null;
        return resolveUserBySubject(jwt.getSubject())
                .or(() -> userRepository.findByEmail(resolveEmail(jwt)))
                .orElse(null);
    }

    private UUID resolveCreatorUserId(Jwt jwt) {
        if (jwt == null) return null;
        User user = resolveCurrentUser(jwt);
        return user != null ? user.getId() : null;
    }

    private String resolveEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            email = jwt.getClaimAsString("preferred_username");
        }
        if (email == null || email.isBlank()) {
            throw new BadRequestException("error.auth.email_missing", new Object[0], "Token does not contain an email.");
        }
        return email;
    }

    private Optional<String> resolveSubject(Jwt jwt) {
        return jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank() 
            ? Optional.empty() : Optional.of(jwt.getSubject());
    }

    private Optional<UUID> resolveSubjectAsUuid(String subject) {
        if (subject == null || subject.isBlank()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(subject));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Optional<User> resolveUserBySubject(String subject) {
        return resolveSubjectAsUuid(subject).flatMap(userRepository::findById);
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

    private Set<String> extractRoles(Jwt jwt) {
        Set<String> roles = new java.util.LinkedHashSet<>();
        Object realmAccessClaim = jwt.getClaim("realm_access");
        if (realmAccessClaim instanceof java.util.Map<?, ?> realmAccess) {
            Object roleValues = realmAccess.get("roles");
            if (roleValues instanceof java.util.Collection<?> collection) {
                for (Object role : collection) {
                    if (role instanceof String roleName) roles.add(roleName.toUpperCase());
                }
            }
        }

        Object resourceAccessClaim = jwt.getClaim("resource_access");
        if (resourceAccessClaim instanceof java.util.Map<?, ?> resourceAccess) {
            for (Object clientAccess : resourceAccess.values()) {
                if (clientAccess instanceof java.util.Map<?, ?> clientRolesMap) {
                    Object roleValues = clientRolesMap.get("roles");
                    if (roleValues instanceof java.util.Collection<?> collection) {
                        for (Object role : collection) {
                            if (role instanceof String roleName) roles.add(roleName.toUpperCase());
                        }
                    }
                }
            }
        }
        return roles;
    }

    private String formatUuidList(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return "[]";
        return ids.stream().map(UUID::toString).collect(Collectors.joining(", ", "[", "]"));
    }

    // ── Entitás → DTO konverzió ───────────────────────────────────────────────

    private TeamResponse toResponse(Team team) {
        List<TeamResponse.MemberInfo> players = team.getPlayers().stream()
                .map(p -> new TeamResponse.MemberInfo(p.getId(), p.getFirstName(), p.getLastName()))
                .toList();

        List<TeamResponse.MemberInfo> coaches = team.getCoaches().stream()
                .map(c -> new TeamResponse.MemberInfo(c.getId(), c.getFirstName(), c.getLastName()))
                .toList();

        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getShortName(),
                team.getLogoUrl(),
                players,
                coaches
        );
    }
}