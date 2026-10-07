package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.InviteKind;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.PlayerParent;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.TeamInvite;
import com.example.footballanalysis.model.db.TeamRoster;
import com.example.footballanalysis.model.db.TeamRosterId;
import com.example.footballanalysis.model.requests.CreatePlayerProfileRequest;
import com.example.footballanalysis.model.requests.CreateTeamRequest;
import com.example.footballanalysis.model.requests.UpdateTeamRequest;
import com.example.footballanalysis.paging.PageQuery;
import com.example.footballanalysis.model.responses.TeamCatalogItem;
import com.example.footballanalysis.model.responses.TeamCatalogResponse;
import com.example.footballanalysis.model.responses.TeamResponse;
import com.example.footballanalysis.repository.ClipRepository;
import com.example.footballanalysis.repository.ClubRepository;
import com.example.footballanalysis.repository.CupTeamRepository;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.MatchSquadMemberRepository;
import com.example.footballanalysis.repository.PlayerParentRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.PlayerRepository;
import com.example.footballanalysis.repository.TeamInviteRepository;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import com.example.footballanalysis.repository.TeamRosterRepository;
import com.example.footballanalysis.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /** Ennyi saját csapat még egyben kilistázható. Ennél többnél üres a lista, amíg nincs keresőszó. */
    static final int COMPLETE_LIST_LIMIT = 8;
    /** Egy keresés legfeljebb ennyi sort ad vissza. A total jelzi, ha van még találat. */
    static final int SEARCH_RESULT_LIMIT = 20;

    private final TeamRepository teamRepository;
    private final ClubRepository clubRepository;
    private final PlayerRepository playerRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final TeamRosterRepository teamRosterRepository;
    private final PlayerParentRepository playerParentRepository;
    private final TeamInviteRepository teamInviteRepository;
    private final MatchRepository matchRepository;
    private final CupTeamRepository cupTeamRepository;
    private final ClipRepository clipRepository;
    private final MatchSquadMemberRepository matchSquadMemberRepository;
    private final UserRepository userRepository;
    private final MinioObjectCleanupService minioObjectCleanupService;
    private final AuditEventService auditEventService;
    private final UserAccessService userAccessService;
    private final TeamMembershipQuery teamMembershipQuery;
    private final ExtraHatService extraHatService;

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
    private List<TeamResponse> getMyTeamsForUser(User user, UserRole role) {
        if (user == null) return List.of();
        if (role == null) {
            throw new BadRequestException(
                    "error.auth.role_missing", new Object[0], "A role-specific team view is required.");
        }

        List<Team> teams = teamRepository.findAllById(userAccessService.getAccessibleTeamIds(user, role));

        return teams.stream().map(team -> toVisibleResponse(team, user)).toList();
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> getMyTeams(org.springframework.security.oauth2.jwt.Jwt jwt, UserRole role) {
        User user = userAccessService.resolveCurrentUser(jwt);
        return getMyTeamsForUser(user, role);
    }

    /**
     * Könnyű csapatlista. A név, a rövid név és a klubnév kereshető. A keret nincs a válaszban.
     * Ha {@code page} vagy {@code size} érkezik, a lista lapozható. Enélkül a választók összesítője jön:
     * legfeljebb {@link #COMPLETE_LIST_LIMIT} saját csapat, keresésnél az első {@link #SEARCH_RESULT_LIMIT} találat.
     */
    @Transactional(readOnly = true)
    public TeamCatalogResponse listCatalog(org.springframework.security.oauth2.jwt.Jwt jwt, UserRole role, String query) {
        return listCatalog(jwt, role, query, null, null);
    }

    @Transactional(readOnly = true)
    public TeamCatalogResponse listCatalog(
            org.springframework.security.oauth2.jwt.Jwt jwt,
            UserRole role,
            String query,
            Integer page,
            Integer size) {
        User user = userAccessService.resolveCurrentUser(jwt);
        if (user == null) {
            return emptyCatalog(page, size);
        }
        if (role == null) {
            throw new BadRequestException(
                    "error.auth.role_missing", new Object[0], "A role-specific team view is required.");
        }

        boolean browse = page != null || size != null;
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String pattern = needle.isEmpty() ? null : "%" + escapeLike(needle) + "%";
        if (role == UserRole.ADMIN) {
            // A role paraméter önmagában nem jogosít. Edző, klubadmin, játékos, szülő és rajongó is hívhatja a végpontot.
            if (!userAccessService.hasHat(user, UserRole.ADMIN)) {
                return emptyCatalog(page, size);
            }
            long total = pattern == null ? teamRepository.count() : countCatalog(role, Set.of(), pattern);
            return browse
                    ? browseCatalog(role, Set.of(), pattern, page, size, total)
                    : summaryCatalog(role, Set.of(), pattern, total);
        }

        Set<UUID> accessible = userAccessService.getAccessibleTeamIds(user, role);
        if (accessible.isEmpty()) {
            return emptyCatalog(page, size);
        }
        long total = pattern == null ? accessible.size() : countCatalog(role, accessible, pattern);
        return browse
                ? browseCatalog(role, accessible, pattern, page, size, total)
                : summaryCatalog(role, accessible, pattern, total);
    }

    private TeamCatalogResponse emptyCatalog(Integer page, Integer size) {
        if (page == null && size == null) {
            return new TeamCatalogResponse(List.of(), 0, false, 1, 0, 0);
        }
        int resolvedPage = PageQuery.page(page);
        int resolvedSize = PageQuery.size(size);
        return new TeamCatalogResponse(List.of(), 0, false, resolvedPage, resolvedSize, 0);
    }

    /** Választó és számláló: sok csapatnál üres, keresésnél csak az első szelet. */
    private TeamCatalogResponse summaryCatalog(UserRole role, Set<UUID> accessible, String pattern, long total) {
        if (total == 0) {
            return new TeamCatalogResponse(List.of(), 0, false, 1, 0, 0);
        }
        if (pattern == null && total > COMPLETE_LIST_LIMIT) {
            return new TeamCatalogResponse(List.of(), total, true, 1, 0, 0);
        }
        int limit = pattern == null ? COMPLETE_LIST_LIMIT : SEARCH_RESULT_LIMIT;
        List<TeamCatalogItem> items = toCatalogItems(loadCatalog(role, accessible, pattern, 1, limit));
        return new TeamCatalogResponse(
                items, total, total > items.size(), 1, limit, PageQuery.totalPages(total, limit));
    }

    /** Listaoldal: üres keresőnél is a kért lap jön, a total és a totalPages a teljes találati halmaz. */
    private TeamCatalogResponse browseCatalog(
            UserRole role, Set<UUID> accessible, String pattern, Integer page, Integer size, long total) {
        int resolvedPage = PageQuery.page(page);
        int resolvedSize = PageQuery.size(size);
        int totalPages = PageQuery.totalPages(total, resolvedSize);
        if (total == 0 || resolvedPage > totalPages) {
            return new TeamCatalogResponse(List.of(), total, false, resolvedPage, resolvedSize, totalPages);
        }
        List<TeamCatalogItem> items = toCatalogItems(loadCatalog(role, accessible, pattern, resolvedPage, resolvedSize));
        boolean partial = (long) resolvedPage * resolvedSize < total;
        return new TeamCatalogResponse(items, total, partial, resolvedPage, resolvedSize, totalPages);
    }

    private List<Team> loadCatalog(UserRole role, Set<UUID> accessible, String pattern, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        return role == UserRole.ADMIN
                ? teamRepository.findCatalog(pattern, pageable)
                : teamRepository.findCatalogByIds(accessible, pattern, pageable);
    }

    private long countCatalog(UserRole role, Set<UUID> accessible, String pattern) {
        return role == UserRole.ADMIN
                ? teamRepository.countCatalog(pattern)
                : teamRepository.countCatalogByIds(accessible, pattern);
    }

    private List<TeamCatalogItem> toCatalogItems(List<Team> teams) {
        if (teams == null || teams.isEmpty()) {
            return List.of();
        }
        List<UUID> teamIds = teams.stream().map(Team::getId).toList();
        Map<UUID, Long> playerCounts = countsByTeam(teamRosterRepository.countByTeamIds(teamIds));
        Map<UUID, Long> coachCounts = teamMembershipQuery.countCoachesByTeamIds(teamIds);
        return teams.stream().map(team -> toCatalogItem(team, playerCounts, coachCounts)).toList();
    }

    @Transactional(readOnly = true)
    public TeamResponse getTeam(UUID id, User actor) {
        log.debug("Fetching team with ID: {}", id);
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{id}, "Team not found: " + id));
        Club club = team.getClub();
        if (!userAccessService.isClubAdmin(actor, club != null ? club.getId() : null)) {
            userAccessService.requireTeamAccess(actor, List.of(id));
        }
        return toVisibleResponse(team, actor);
    }

    public TeamResponse describeTeam(Team team) {
        return toResponse(team);
    }

    // ── Csapat módosítások (Create, Update, Delete) ───────────────────────────

    @Transactional
    public TeamResponse createTeam(CreateTeamRequest request, User actor) {
        requireTeamCreationPermission(actor, request.clubId());

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
        team.setClub(resolveClubForNewTeam(request, name, actor));

        Team savedTeam = teamRepository.save(team);

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

        return toVisibleResponse(savedTeam, actor);
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

        userAccessService.requireClubTeamManage(actor, id);

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
                
         return toVisibleResponse(teamRepository.save(team), actor);
    }

    @Transactional
    public void deleteTeam(UUID id, User actor) {
        log.info("Attempting to delete team with ID: {}", id);
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{id}, "Team not found: " + id));

        userAccessService.requireClubTeamManage(actor, id);

        log.debug("Deleting related matching data and clips for team: {}", id);
        List<Match> matches = matchRepository.findAllByHomeTeam_IdOrAwayTeam_Id(id, id);
        List<Match> homeMatches = new ArrayList<>();
        List<Match> awayOnlyMatches = new ArrayList<>();
        for (Match match : matches) {
            Team home = match.getHomeTeam();
            if (home != null && id.equals(home.getId())) {
                homeMatches.add(match);
            } else {
                awayOnlyMatches.add(match);
            }
        }
        for (Match match : awayOnlyMatches) {
            match.setAwayTeam(null);
        }
        if (!awayOnlyMatches.isEmpty()) {
            matchRepository.saveAll(awayOnlyMatches);
        }

        List<UUID> matchIds = homeMatches.stream().map(Match::getId).toList();
        List<Clip> clips = matchIds.isEmpty() ? List.of() : clipRepository.findAllByMatch_IdIn(matchIds);

        clearTeamMemberships(team);
        rehomeOpenChildLoginInvites(id);
        teamInviteRepository.deleteAllByTeam_Id(id);
        matchSquadMemberRepository.deleteAllByTeam_Id(id);
        cupTeamRepository.clearRealTeamByTeamId(id);

        if (!matchIds.isEmpty()) {
            clipRepository.deleteAllByMatch_IdIn(matchIds);
            matchRepository.deleteAll(homeMatches);
        }

        teamRepository.delete(team);
        teamRepository.flush();

        minioObjectCleanupService.deleteMatchArtifactsForTeamDeletion(homeMatches, clips);
        
        auditEventService.record(
            "TEAM_DELETED",
            actor != null ? actor.getId() : null,
            actor != null ? actor.getRole().name() : null,
            "TEAM",
            id.toString(),
            "matchIds=" + formatUuidList(matchIds) + ", clipIds=" + formatUuidList(clips.stream().map(Clip::getId).toList())
        );

        log.info("Team successfully deleted: teamId={}, matchesDeleted={}, awayMatchesUnlinked={}, clipsDeleted={}",
                id, homeMatches.size(), awayOnlyMatches.size(), clips.size());
    }



    private void clearTeamMemberships(Team team) {
        UUID teamId = team.getId();
        List<UUID> coachIds = idsOrEmpty(teamMembershipQuery.findCoachIdsByTeamId(teamId));
        List<UUID> fanIds = idsOrEmpty(teamMembershipQuery.findFanIdsByTeamId(teamId));
        List<PlayerProfile> profiles = playerProfileRepository.findByTeam_Id(teamId);
        List<UUID> linkedPlayerIds = profiles == null ? List.of() : profiles.stream()
                .map(PlayerProfile::getLinkedUser)
                .filter(Objects::nonNull)
                .map(User::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        teamRosterRepository.deleteByTeam_Id(teamId);
        teamMembershipQuery.deleteCoachTeamsForTeam(teamId);
        teamMembershipQuery.deleteFanTeamsForTeam(teamId);
        team.getCoaches().clear();

        for (UUID coachId : coachIds) {
            if (noTeams(teamMembershipQuery.findCoachTeamIds(coachId))) {
                extraHatService.revokeHat(coachId, UserRole.COACH);
            }
        }
        for (UUID fanId : fanIds) {
            if (noTeams(teamMembershipQuery.findFanTeamIds(fanId))) {
                extraHatService.revokeHat(fanId, UserRole.FAN);
            }
        }
        for (UUID linkedUserId : linkedPlayerIds) {
            if (noTeams(playerProfileRepository.findTeamIdsByLinkedUserId(linkedUserId))) {
                extraHatService.revokeHat(linkedUserId, UserRole.PLAYER);
            }
        }
        log.debug("Cleared team memberships for team: {}", teamId);
    }

    private static boolean noTeams(List<UUID> teamIds) {
        return teamIds == null || teamIds.isEmpty();
    }

    private static List<UUID> idsOrEmpty(List<UUID> ids) {
        return ids == null ? List.of() : ids;
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
        if (enforceTeamAccess) {
            requireDirectPlayerAssign(actor, team);
        }
        ensureClub(team);

        User player = userRepository.findById(playerId).orElseThrow(() -> new NotFoundException(
                "error.user.not_found", new Object[]{playerId}, "User not found."));
        extraHatService.requireTeamRoleFree(playerId, teamId, UserRole.PLAYER);
        PlayerProfile profile = playerProfileRepository.findByLinkedUser_Id(playerId).orElse(null);
        if (profile == null) {
            profile = new PlayerProfile();
            profile.setLinkedUser(player);
            profile.setFirstName(nameOrEmpty(player.getFirstName()));
            profile.setLastName(nameOrEmpty(player.getLastName()));
            profile = playerProfileRepository.save(profile);
        }
        addProfileToTeam(profile, team);
        extraHatService.grantHat(playerId, UserRole.PLAYER);
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
        requireDirectPlayerAssign(actor, team);
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
        if (actor != null) userAccessService.requireClubTeamManage(actor, teamId);

        PlayerProfile profile = playerProfileRepository.findById(profileId)
                .orElseThrow(() -> new NotFoundException("error.player_profile.not_found", new Object[]{profileId}, "Player profile not found."));
        TeamRosterId rosterId = new TeamRosterId(profileId, teamId);
        if (!teamRosterRepository.existsById(rosterId)) {
            throw new ConflictException("error.team.player.not_member", new Object[0], "Player not in team.");
        }

        teamRosterRepository.deleteById(rosterId);
        UUID linkedUserId = profile.getLinkedUser() != null ? profile.getLinkedUser().getId() : null;
        if (linkedUserId != null && playerProfileRepository.findTeamIdsByLinkedUserId(linkedUserId).isEmpty()) {
            extraHatService.revokeHat(linkedUserId, UserRole.PLAYER);
        }
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
        if (enforceTeamAccess && actor != null) userAccessService.requireClubTeamManage(actor, teamId);

        userRepository.findById(coachId).orElseThrow(() -> new NotFoundException(
                "error.user.not_found", new Object[]{coachId}, "User not found."));
        extraHatService.requireTeamRoleFree(coachId, teamId, UserRole.COACH);
        if (teamMembershipQuery.isCoachOnTeam(coachId, teamId)) {
            throw new ConflictException("error.team.coach.already_member", new Object[0], "Coach already in team.");
        }

        teamMembershipQuery.insertCoachTeam(coachId, teamId);
        extraHatService.grantHat(coachId, UserRole.COACH);
        recordMemberChange("TEAM_COACH_ADDED", actor, teamId, "coachId", coachId);
        log.info("Added coach {} to team {}", coachId, teamId);
    }



    @Transactional
    public void removeCoachFromTeam(UUID teamId, UUID coachId, User actor) {
        Team team = teamRepository.findById(teamId).orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found."));
        if (actor != null) userAccessService.requireClubTeamManage(actor, teamId);

        userRepository.findById(coachId).orElseThrow(() -> new NotFoundException(
                "error.user.not_found", new Object[]{coachId}, "User not found."));
        if (!teamMembershipQuery.isCoachOnTeam(coachId, teamId)) {
            throw new ConflictException("error.team.coach.not_member", new Object[0], "Coach not in team.");
        }

        teamMembershipQuery.deleteCoachTeam(coachId, teamId);
        if (teamMembershipQuery.findCoachTeamIds(coachId).isEmpty()) {
            extraHatService.revokeHat(coachId, UserRole.COACH);
        }
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
        if (enforceTeamAccess && actor != null) userAccessService.requireClubTeamManage(actor, teamId);

        userRepository.findById(fanId).orElseThrow(() -> new NotFoundException(
                "error.user.not_found", new Object[]{fanId}, "User not found."));
        extraHatService.requireTeamRoleFree(fanId, teamId, UserRole.FAN);
        if (teamMembershipQuery.isFanOnTeam(fanId, teamId)) {
            throw new ConflictException("error.team.fan.already_following", new Object[0], "Fan already following team.");
        }

        teamMembershipQuery.insertFanTeam(fanId, teamId);
        extraHatService.grantHat(fanId, UserRole.FAN);
        recordMemberChange("TEAM_FAN_ADDED", actor, teamId, "fanId", fanId);
        log.info("Added fan {} to team {}", fanId, teamId);
    }



     @Transactional
     public void removeFanFromTeam(UUID teamId, UUID fanId, User actor) {
        Team team = teamRepository.findById(teamId).orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found."));
        if (actor != null) userAccessService.requireClubTeamManage(actor, teamId);

        userRepository.findById(fanId).orElseThrow(() -> new NotFoundException(
                "error.user.not_found", new Object[]{fanId}, "User not found."));
        if (!teamMembershipQuery.isFanOnTeam(fanId, teamId)) {
            throw new ConflictException("error.team.fan.not_following", new Object[0], "Fan not following team.");
        }

        teamMembershipQuery.deleteFanTeam(fanId, teamId);
        if (teamMembershipQuery.findFanTeamIds(fanId).isEmpty()) {
            extraHatService.revokeHat(fanId, UserRole.FAN);
        }
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

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Map<UUID, Long> countsByTeam(List<Object[]> rows) {
        Map<UUID, Long> counts = new HashMap<>();
        if (rows == null) {
            return counts;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            UUID id = row[0] instanceof UUID uuid ? uuid : UUID.fromString(row[0].toString());
            counts.put(id, ((Number) row[1]).longValue());
        }
        return counts;
    }

    private static TeamCatalogItem toCatalogItem(Team team, Map<UUID, Long> playerCounts, Map<UUID, Long> coachCounts) {
        Club club = team.getClub();
        return new TeamCatalogItem(
                team.getId(),
                team.getName(),
                team.getShortName(),
                team.getLogoUrl(),
                club != null ? club.getId() : null,
                club != null ? club.getName() : null,
                playerCounts.getOrDefault(team.getId(), 0L),
                coachCounts.getOrDefault(team.getId(), 0L));
    }

    private TeamResponse toVisibleResponse(Team team, User actor) {
        return toResponse(team, canSeeRosterDetails(actor, team));
    }

    private boolean canSeeRosterDetails(User actor, Team team) {
        if (userAccessService.isAdmin(actor)) {
            return true;
        }
        Club club = team.getClub();
        return userAccessService.isClubAdmin(actor, club != null ? club.getId() : null);
    }

    private TeamResponse toResponse(Team team) {
        return toResponse(team, true);
    }

    private List<TeamResponse.MemberInfo> membersFromUserIds(
            List<UUID> userIds, boolean includeContactDetails, Comparator<String> nameComparator) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllById(userIds).stream()
                .sorted(Comparator.comparing(User::getLastName, nameComparator)
                        .thenComparing(User::getFirstName, nameComparator)
                        .thenComparing(User::getId))
                .map(user -> new TeamResponse.MemberInfo(
                        user.getId(),
                        user.getFirstName(),
                        user.getLastName(),
                        null,
                        includeContactDetails ? user.getEmail() : null,
                        null))
                .toList();
    }

    private TeamResponse toResponse(Team team, boolean includeContactDetails) {
        Comparator<String> nameComparator = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);

        List<TeamResponse.MemberInfo> players = playerProfileRepository.findByTeam_Id(team.getId()).stream()
                .sorted(Comparator.comparing(PlayerProfile::getLastName, nameComparator)
                        .thenComparing(PlayerProfile::getFirstName, nameComparator)
                        .thenComparing(PlayerProfile::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(p -> new TeamResponse.MemberInfo(
                        p.getId(),
                        p.getFirstName(),
                        p.getLastName(),
                        p.getLinkedUser() != null ? p.getLinkedUser().getId() : null,
                        includeContactDetails && p.getLinkedUser() != null ? p.getLinkedUser().getEmail() : null,
                        includeContactDetails ? p.getDateOfBirth() : null))
                .toList();

        List<TeamResponse.MemberInfo> coaches = membersFromUserIds(
                teamMembershipQuery.findCoachIdsByTeamId(team.getId()), includeContactDetails, nameComparator);

        List<TeamResponse.MemberInfo> fans = includeContactDetails
                ? membersFromUserIds(
                        teamMembershipQuery.findFanIdsByTeamId(team.getId()), true, nameComparator)
                : List.of();

        List<TeamResponse.MemberInfo> parents = includeContactDetails
                ? userRepository.findParentsOnTeam(team.getId()).stream()
                    .map(parent -> new TeamResponse.MemberInfo(parent.getId(), parent.getFirstName(), parent.getLastName(), null, parent.getEmail(), null))
                    .toList()
                : List.of();

        Club club = team.getClub();
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getShortName(),
                team.getLogoUrl(),
                club != null ? club.getId() : null,
                club != null ? club.getName() : null,
                players,
                coaches,
                fans,
                parents
        );
    }

    @Transactional
    public void removeParentFromTeam(UUID teamId, UUID parentId, User actor) {
        if (actor != null) userAccessService.requireClubTeamManage(actor, teamId);
        List<PlayerParent> links = playerParentRepository.findLinksForParentOnTeam(parentId, teamId);
        if (links.isEmpty()) {
            throw new ConflictException("error.player_parent.link_not_found", new Object[]{parentId}, "Parent is not linked to this team.");
        }
        for (PlayerParent link : links) {
            PlayerProfile profile = link.getPlayerProfile();
            if (profile == null || profile.getId() == null) {
                continue;
            }
            if (teamRosterRepository.existsByPlayerProfile_IdAndTeam_IdNot(profile.getId(), teamId)) {
                throw new ConflictException(
                        "error.player_parent.other_teams",
                        new Object[0],
                        "Unlink this parent from the player profile. Removing them from this team would also remove them from the player's other teams.");
            }
            if (profile.getLinkedUser() != null) {
                continue;
            }
            if (playerParentRepository.findByPlayerProfile_Id(profile.getId()).size() <= 1) {
                throw new ConflictException(
                        "error.player_parent.last_without_login",
                        new Object[0],
                        "Create a child login before removing the last parent.");
            }
        }
        playerParentRepository.deleteAll(links);
        playerParentRepository.flush();
        revokeParentHatIfUnlinked(parentId);
        recordMemberChange("TEAM_PARENT_REMOVED", actor, teamId, "parentId", parentId);
    }

    private void revokeParentHatIfUnlinked(UUID parentId) {
        if (parentId == null) {
            return;
        }
        List<PlayerParent> remaining = playerParentRepository.findByUser_Id(parentId);
        if (remaining == null || remaining.isEmpty()) {
            if (!userAccessService.isOnlyRemainingHat(parentId, UserRole.PARENT)) {
                extraHatService.revokeHat(parentId, UserRole.PARENT);
            }
        }
    }

    private void rehomeOpenChildLoginInvites(UUID teamId) {
        List<TeamInvite> invites = teamInviteRepository.findByTeam_IdAndInviteKindAndUsedCount(
                teamId, InviteKind.CHILD_LOGIN, 0);
        if (invites == null || invites.isEmpty()) {
            return;
        }
        for (TeamInvite invite : invites) {
            PlayerProfile profile = invite.getPlayerProfile();
            if (profile == null || profile.getId() == null) {
                continue;
            }
            List<Team> remaining = playerProfileRepository.findTeamsByProfileId(profile.getId());
            Team next = remaining == null ? null : remaining.stream()
                    .filter(team -> team != null && team.getId() != null && !teamId.equals(team.getId()))
                    .findFirst()
                    .orElse(null);
            if (next == null) {
                continue;
            }
            invite.setTeam(next);
            teamInviteRepository.save(invite);
        }
        teamInviteRepository.flush();
    }

    private Club resolveClubForNewTeam(CreateTeamRequest request, String teamName, User actor) {
        if (request.clubId() != null) {
            Club club = clubRepository.findById(request.clubId())
                    .orElseThrow(() -> new NotFoundException(
                            "error.club.not_found",
                            new Object[]{request.clubId()},
                            "Club not found: " + request.clubId()));
            return club;
        }
        Club club = new Club();
        club.setName(teamName);
        club.setShortName(request.shortName());
        club.setLogoUrl(request.logoUrl());
        club.setCreatedByUserId(actor.getId());
        Club saved = clubRepository.save(club);
        auditEventService.record(
                "CLUB_CREATED",
                actor.getId(),
                actor.getRole() != null ? actor.getRole().name() : null,
                "CLUB",
                saved.getId().toString(),
                "name=" + saved.getName());
        return saved;
    }

    private void requireTeamCreationPermission(User actor, UUID clubId) {
        if (userAccessService.isAdmin(actor)) {
            return;
        }
        if (clubId == null) {
            throw new BadRequestException("error.club.required", new Object[0], "A club is required.");
        }
        if (userAccessService.isClubAdmin(actor, clubId)) {
            return;
        }
        throw new ForbiddenException(
                "error.auth.forbidden", new Object[0], "Only a platform or club admin can create a team.");
    }

    /**
     * A coach does not place a player on the roster. The player, or a parent,
     * joins through an invite. Platform admin and the club's admin may still
     * attach a profile or an existing account.
     */
    private void requireDirectPlayerAssign(User actor, Team team) {
        if (actor == null) {
            return;
        }
        if (userAccessService.isAdmin(actor)) {
            return;
        }
        UUID clubId = team.getClub() != null ? team.getClub().getId() : null;
        if (userAccessService.isClubAdmin(actor, clubId)) {
            return;
        }
        throw new ForbiddenException(
                "error.team.player.invite_only",
                new Object[0],
                "A coach adds a player by sending an invite.");
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
