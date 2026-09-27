package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Fan;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.TeamRoster;
import com.example.footballanalysis.model.db.TeamRosterId;
import com.example.footballanalysis.model.requests.CreatePlayerProfileRequest;
import com.example.footballanalysis.model.requests.CreateTeamRequest;
import com.example.footballanalysis.model.requests.UpdateTeamRequest;
import com.example.footballanalysis.model.responses.TeamResponse;
import com.example.footballanalysis.repository.ClipRepository;
import com.example.footballanalysis.repository.ClubRepository;
import com.example.footballanalysis.repository.CoachRepository;
import com.example.footballanalysis.repository.FanRepository;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.MatchSquadMemberRepository;
import com.example.footballanalysis.repository.PlayerParentRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.PlayerRepository;
import com.example.footballanalysis.repository.TeamInviteRepository;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.TeamRosterRepository;
import com.example.footballanalysis.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamService {

    private final TeamRepository teamRepository;
    private final ClubRepository clubRepository;
    private final PlayerRepository playerRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final TeamRosterRepository teamRosterRepository;
    private final PlayerParentRepository playerParentRepository;
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
    private List<TeamResponse> getMyTeamsForUser(User user) {
        if (user == null || user.getRole() == null) return List.of();

        List<Team> teams = switch (user.getRole()) {
            case ADMIN -> teamRepository.findAll();
            case COACH -> user instanceof Coach coach ? coach.getTeams().stream().toList() : List.of();
            case PLAYER -> playerProfileRepository.findTeamsByLinkedUserId(user.getId());
            case FAN -> user instanceof Fan fan ? fan.getTeams().stream().toList() : List.of();
            case PARENT -> playerParentRepository.findByUser_Id(user.getId()).stream()
                    .map(link -> link.getPlayerProfile())
                    .filter(Objects::nonNull)
                    .flatMap(profile -> playerProfileRepository.findTeamsByProfileId(profile.getId()).stream())
                    .collect(Collectors.toMap(Team::getId, team -> team, (left, right) -> left, java.util.LinkedHashMap::new))
                    .values().stream().toList();
        };

        return teams.stream().map(this::toResponse).toList();
    }

    // Backward-compatible overload accepting Jwt (used by older callers/tests)
    @Transactional(readOnly = true)
    public List<TeamResponse> getMyTeams(org.springframework.security.oauth2.jwt.Jwt jwt) {
        User user = userAccessService.resolveCurrentUser(jwt);
        return getMyTeamsForUser(user);
    }

    @Transactional(readOnly = true)
    public TeamResponse getTeam(UUID id, User actor) {
        log.debug("Fetching team with ID: {}", id);
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{id}, "Team not found: " + id));
        userAccessService.requireTeamAccess(actor, List.of(id));
        return toResponse(team);
    }

    // ── Csapat módosítások (Create, Update, Delete) ───────────────────────────

    @Transactional
    public TeamResponse createTeam(CreateTeamRequest request, User actor) {
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
        team.setClub(resolveClubForNewTeam(request, name));

        Team savedTeam = teamRepository.save(team);
        
        if (actor != null && actor.getRole() == UserRole.COACH) {
            if (!(actor instanceof Coach coach)) {
                throw new BadRequestException("error.team.invalid_actor", new Object[0], "Actor is not a coach.");
            }
            coach.addTeam(savedTeam);
            coachRepository.save(coach);
        }

        auditEventService.record(
            "TEAM_CREATED",
            actor != null ? actor.getId() : null,
            actor != null ? actor.getRole().name() : null,
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
    public TeamResponse createTeam(CreateTeamRequest request, org.springframework.security.oauth2.jwt.Jwt jwt) {
        User actor = jwt == null ? null : userAccessService.resolveCurrentUser(jwt);
        return createTeam(request, actor);
    }



    @Transactional
    public TeamResponse updateTeam(UUID id, UpdateTeamRequest request, User actor) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{id}, "Team not found: " + id));

        userAccessService.requireTeamAccess(actor, List.of(id));

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
    public void deleteTeam(UUID id, User actor) {
        log.info("Attempting to delete team with ID: {}", id);
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{id}, "Team not found: " + id));

        userAccessService.requireTeamAccess(actor, List.of(id));

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
            actor != null ? actor.getRole().name() : null,
            "TEAM",
            id.toString(),
            "matchIds=" + formatUuidList(matchIds) + ", clipIds=" + formatUuidList(clips.stream().map(Clip::getId).toList())
        );

        log.info("Team successfully deleted: teamId={}, matchesDeleted={}, clipsDeleted={}", id, matches.size(), clips.size());
    }



    private void clearTeamMemberships(Team team) {
        teamRosterRepository.deleteByTeam_Id(team.getId());
        coachRepository.removeAllCoachesFromTeam(team.getId());
        fanRepository.removeAllFansFromTeam(team.getId());
        team.getCoaches().clear();
        log.debug("Cleared team memberships for team: {}", team.getId());
    }

    private String formatUuidList(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return "[]";
        return ids.stream().map(UUID::toString).collect(Collectors.joining(", ", "[", "]"));
    }

    // ── Tagok kezelése (Player, Coach, Fan) ───────────────────────────────────

    @Transactional
    public void addPlayerToTeam(UUID teamId, UUID playerId, User actor) {
        addPlayerToTeam(teamId, playerId, actor, true);
    }

    @Transactional
    public void addPlayerToTeam(UUID teamId, UUID playerId, User actor, boolean enforceTeamAccess) {
        Team team = teamRepository.findById(teamId).orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found."));
        if (enforceTeamAccess && actor != null) userAccessService.requireTeamAccess(actor, List.of(teamId));
        ensureClub(team);

        Player player = playerRepository.findById(playerId).orElseThrow(() -> new NotFoundException("error.player.not_found", new Object[]{playerId}, "Player not found."));
        PlayerProfile profile = playerProfileRepository.findByLinkedUser_Id(playerId).orElse(null);
        if (profile == null) {
            profile = new PlayerProfile();
            profile.setLinkedUser(player);
            profile.setFirstName(nameOrEmpty(player.getFirstName()));
            profile.setLastName(nameOrEmpty(player.getLastName()));
            profile = playerProfileRepository.save(profile);
        }
        addProfileToTeam(profile, team);
        recordMemberChange("TEAM_PLAYER_ADDED", actor, teamId, "playerId", playerId);
        log.info("Added player {} to team {}", playerId, teamId);
    }

    @Transactional
    public PlayerProfile addRosterProfile(UUID teamId, String firstName, String lastName, LocalDate dateOfBirth) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found."));
        ensureClub(team);
        PlayerProfile profile = new PlayerProfile();
        profile.setFirstName(firstName.trim());
        profile.setLastName(lastName.trim());
        profile.setDateOfBirth(dateOfBirth);
        profile = playerProfileRepository.save(profile);
        addProfileToTeam(profile, team);
        return profile;
    }

    @Transactional
    public void addProfileToTeam(PlayerProfile profile, Team team) {
        if (profile == null || profile.getId() == null) {
            throw new NotFoundException("error.player_profile.not_found", new Object[0], "Player profile is missing.");
        }
        if (team == null || team.getId() == null) {
            throw new NotFoundException("error.team.not_found", new Object[0], "Team is missing.");
        }
        ensureClub(team);
        if (teamRosterRepository.existsByPlayerProfile_IdAndTeam_Id(profile.getId(), team.getId())) {
            throw new ConflictException("error.team.player.already_member", new Object[0], "Player already in team.");
        }
        TeamRoster row = new TeamRoster();
        row.setId(new TeamRosterId(profile.getId(), team.getId()));
        row.setPlayerProfile(profile);
        row.setTeam(team);
        teamRosterRepository.save(row);
    }

    @Transactional
    public void assignLinkedDateOfBirth(UUID userId, LocalDate dateOfBirth) {
        PlayerProfile profile = playerProfileRepository.findByLinkedUser_Id(userId)
                .orElseThrow(() -> new NotFoundException(
                        "error.player_profile.not_found",
                        new Object[]{userId},
                        "Linked player profile not found."));
        profile.setDateOfBirth(dateOfBirth);
        playerProfileRepository.save(profile);
    }

    @Transactional
    public void createPlayerProfile(UUID teamId, CreatePlayerProfileRequest request, User actor) {
        Team team = teamRepository.findById(teamId).orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found."));
        if (actor != null) userAccessService.requireTeamAccess(actor, List.of(teamId));
        ensureClub(team);

        PlayerProfile profile = new PlayerProfile();
        profile.setFirstName(request.firstName().trim());
        profile.setLastName(request.lastName().trim());
        profile.setDateOfBirth(request.dateOfBirth());
        PlayerProfile saved = playerProfileRepository.save(profile);
        addProfileToTeam(saved, team);
        recordMemberChange("TEAM_PLAYER_ADDED", actor, teamId, "profileId", saved.getId());
        log.info("Created player profile {} on team {}", saved.getId(), teamId);
    }

    @Transactional
    public void removePlayerFromTeam(UUID teamId, UUID profileId, User actor) {
        Team team = teamRepository.findById(teamId).orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found."));
        if (actor != null) userAccessService.requireTeamAccess(actor, List.of(teamId));

        if (!playerProfileRepository.existsById(profileId)) {
            throw new NotFoundException("error.player_profile.not_found", new Object[]{profileId}, "Player profile not found.");
        }
        TeamRosterId rosterId = new TeamRosterId(profileId, teamId);
        if (!teamRosterRepository.existsById(rosterId)) {
            throw new ConflictException("error.team.player.not_member", new Object[0], "Player not in team.");
        }

        teamRosterRepository.deleteById(rosterId);
        recordMemberChange("TEAM_PLAYER_REMOVED", actor, teamId, "profileId", profileId);
        log.info("Removed player profile {} from team {}", profileId, teamId);
    }



    @Transactional
    public void addCoachToTeam(UUID teamId, UUID coachId, User actor) {
        addCoachToTeam(teamId, coachId, actor, true);
    }

    @Transactional
    public void addCoachToTeam(UUID teamId, UUID coachId, User actor, boolean enforceTeamAccess) {
        Team team = teamRepository.findById(teamId).orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found."));
        if (enforceTeamAccess && actor != null) userAccessService.requireTeamAccess(actor, List.of(teamId));

        Coach coach = coachRepository.findById(coachId).orElseThrow(() -> new NotFoundException("error.coach.not_found", new Object[]{coachId}, "Coach not found."));
        if (team.getCoaches().contains(coach)) throw new ConflictException("error.team.coach.already_member", new Object[0], "Coach already in team.");

        coach.addTeam(team);
        coachRepository.save(coach);
        recordMemberChange("TEAM_COACH_ADDED", actor, teamId, "coachId", coachId);
        log.info("Added coach {} to team {}", coachId, teamId);
    }



    @Transactional
    public void removeCoachFromTeam(UUID teamId, UUID coachId, User actor) {
        Team team = teamRepository.findById(teamId).orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found."));
        if (actor != null) userAccessService.requireTeamAccess(actor, List.of(teamId));

        Coach coach = coachRepository.findById(coachId).orElseThrow(() -> new NotFoundException("error.coach.not_found", new Object[]{coachId}, "Coach not in team."));
        if (!team.getCoaches().contains(coach)) throw new ConflictException("error.team.coach.not_member", new Object[0], "Coach not in team.");

        coach.removeTeam(team);
        coachRepository.save(coach);
        recordMemberChange("TEAM_COACH_REMOVED", actor, teamId, "coachId", coachId);
        log.info("Removed coach {} from team {}", coachId, teamId);
    }



    @Transactional
    public void addFanToTeam(UUID teamId, UUID fanId, User actor) {
        addFanToTeam(teamId, fanId, actor, true);
    }

    @Transactional
    public void addFanToTeam(UUID teamId, UUID fanId, User actor, boolean enforceTeamAccess) {
        Team team = teamRepository.findById(teamId).orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found."));
        if (enforceTeamAccess && actor != null) userAccessService.requireTeamAccess(actor, List.of(teamId));

        Fan fan = fanRepository.findById(fanId).orElseThrow(() -> new NotFoundException("error.fan.not_found", new Object[]{fanId}, "Fan not found."));
        if (fan.getTeams().contains(team)) throw new ConflictException("error.team.fan.already_following", new Object[0], "Fan already following team.");

        fan.addTeam(team);
        fanRepository.save(fan);
        recordMemberChange("TEAM_FAN_ADDED", actor, teamId, "fanId", fanId);
        log.info("Added fan {} to team {}", fanId, teamId);
    }



     @Transactional
     public void removeFanFromTeam(UUID teamId, UUID fanId, User actor) {
        Team team = teamRepository.findById(teamId).orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found."));
        if (actor != null) userAccessService.requireTeamAccess(actor, List.of(teamId));

        Fan fan = fanRepository.findById(fanId).orElseThrow(() -> new NotFoundException("error.fan.not_found", new Object[]{fanId}, "Fan not found."));
        if (!fan.getTeams().contains(team)) throw new ConflictException("error.team.fan.not_following", new Object[0], "Fan not following team.");

        fan.removeTeam(team);
        fanRepository.save(fan);
        recordMemberChange("TEAM_FAN_REMOVED", actor, teamId, "fanId", fanId);
        log.info("Removed fan {} from team {}", fanId, teamId);
    }




    private void recordMemberChange(String eventType, User actor, UUID teamId, String memberKey, UUID memberId) {
        auditEventService.record(
                eventType,
                actor != null ? actor.getId() : null,
                actor != null && actor.getRole() != null ? actor.getRole().name() : null,
                "TEAM",
                teamId.toString(),
                memberKey + "=" + memberId
        );
    }

    // ── Entitás → DTO konverzió ───────────────────────────────────────────────

    private TeamResponse toResponse(Team team) {
        Comparator<String> nameComparator = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);

        List<TeamResponse.MemberInfo> players = playerProfileRepository.findByTeam_Id(team.getId()).stream()
                .sorted(Comparator.comparing(PlayerProfile::getLastName, nameComparator)
                        .thenComparing(PlayerProfile::getFirstName, nameComparator)
                        .thenComparing(PlayerProfile::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(p -> new TeamResponse.MemberInfo(
                        p.getId(),
                        p.getFirstName(),
                        p.getLastName(),
                        p.getLinkedUser() != null ? p.getLinkedUser().getId() : null))
                .toList();

        List<TeamResponse.MemberInfo> coaches = team.getCoaches().stream()
                .sorted(Comparator.comparing(Coach::getLastName, nameComparator)
                        .thenComparing(Coach::getFirstName, nameComparator)
                        .thenComparing(Coach::getId))
                .map(c -> new TeamResponse.MemberInfo(c.getId(), c.getFirstName(), c.getLastName()))
                .toList();

        Club club = team.getClub();
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getShortName(),
                team.getLogoUrl(),
                club != null ? club.getId() : null,
                club != null ? club.getName() : null,
                players,
                coaches
        );
    }

    private Club resolveClubForNewTeam(CreateTeamRequest request, String teamName) {
        if (request.clubId() != null) {
            return clubRepository.findById(request.clubId())
                    .orElseThrow(() -> new NotFoundException(
                            "error.club.not_found",
                            new Object[]{request.clubId()},
                            "Club not found: " + request.clubId()));
        }
        Club club = new Club();
        club.setName(teamName);
        club.setShortName(request.shortName());
        club.setLogoUrl(request.logoUrl());
        return clubRepository.save(club);
    }

    private Club ensureClub(Team team) {
        if (team.getClub() != null) {
            return team.getClub();
        }
        Club club = new Club();
        club.setName(team.getName());
        Club saved = clubRepository.save(club);
        team.setClub(saved);
        teamRepository.save(team);
        log.info("Created club {} for team {} that had no club", saved.getId(), team.getId());
        return saved;
    }

    private static String nameOrEmpty(String name) {
        return name == null ? "" : name;
    }
}