package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.PlayerParent;
import com.example.footballanalysis.model.db.PlayerParentId;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.user.Admin;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Fan;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.CreatePlayerProfileRequest;
import com.example.footballanalysis.model.requests.CreateTeamRequest;
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
import com.example.footballanalysis.model.db.TeamRoster;
import com.example.footballanalysis.model.db.TeamRosterId;
import com.example.footballanalysis.repository.TeamInviteRepository;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.TeamRosterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import com.example.footballanalysis.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeamService tesztek")
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private ClubRepository clubRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PlayerProfileRepository playerProfileRepository;

    @Mock
    private TeamRosterRepository teamRosterRepository;

    @Mock
    private PlayerParentRepository playerParentRepository;

    @Mock
    private CoachRepository coachRepository;

    @Mock
    private FanRepository fanRepository;

    @Mock
    private TeamInviteRepository teamInviteRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private ClipRepository clipRepository;

    @Mock
    private MatchSquadMemberRepository matchSquadMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MinioObjectCleanupService minioObjectCleanupService;

    @Mock
    private AuditEventService auditEventService;

    @Mock
    private UserAccessService userAccessService;

    private TeamService createService() {
        return new TeamService(
                teamRepository,
                clubRepository,
                playerRepository,
                playerProfileRepository,
                teamRosterRepository,
                playerParentRepository,
                coachRepository,
                fanRepository,
                teamInviteRepository,
                matchRepository,
                clipRepository,
                matchSquadMemberRepository,
                userRepository,
                minioObjectCleanupService,
                auditEventService,
                userAccessService);
    }

    @Test
    void getMyTeams_returnsCoachTeamsWhenAuthenticatedByKeycloakId() {
        UUID coachId = UUID.randomUUID();
        Team team = team("Arsenal");
        Coach coach = coach(coachId, "coach@test.com");
        coach.setRole(UserRole.COACH);
        coach.addTeam(team);

        Jwt authentication = jwtWithRole(coachId.toString(), "coach@test.com", "COACH");

        when(userAccessService.resolveCurrentUser(authentication)).thenReturn(coach);

        TeamService teamService = createService();
        List<TeamResponse> teams = teamService.getMyTeams(authentication);

        assertThat(teams).hasSize(1);
        assertThat(teams.get(0).name()).isEqualTo("Arsenal");
    }

    @Test
    void getMyTeams_returnsPlayerTeamsWhenUuidLookupIsNeeded() {
        UUID playerId = UUID.randomUUID();
        Team team = team("Barcelona");
        Player player = player(playerId, "player@test.com");
        player.setRole(UserRole.PLAYER);

        Jwt authentication = jwtWithRole(playerId.toString(), "player@test.com", "PLAYER");

        when(userAccessService.resolveCurrentUser(authentication)).thenReturn(player);
        when(playerProfileRepository.findTeamsByLinkedUserId(playerId)).thenReturn(List.of(team));

        TeamService teamService = createService();
        List<TeamResponse> teams = teamService.getMyTeams(authentication);

        assertThat(teams).hasSize(1);
        assertThat(teams.get(0).name()).isEqualTo("Barcelona");
    }

    @Test
    void getMyTeams_returnsFanTeamsWhenEmailFallbackIsUsed() {
        Team team = team("Liverpool");
        Fan fan = fan(UUID.randomUUID(), "fan@test.com");
        fan.setRole(UserRole.FAN);
        fan.addTeam(team);

        Jwt authentication = jwtWithRole("", "fan@test.com", "FAN");

        when(userAccessService.resolveCurrentUser(authentication)).thenReturn(fan);

        TeamService teamService = createService();
        List<TeamResponse> teams = teamService.getMyTeams(authentication);

        assertThat(teams).hasSize(1);
        assertThat(teams.get(0).name()).isEqualTo("Liverpool");
    }

    @Test
    void getMyTeams_returnsAllTeamsForAdmin() {
        Team team = team("Arsenal");

        Jwt authentication = jwtWithRole(UUID.randomUUID().toString(), "admin@test.com", "ADMIN");

        Admin admin = new Admin();
        admin.setId(UUID.fromString(authentication.getSubject()));
        admin.setRole(UserRole.ADMIN);
        when(userAccessService.resolveCurrentUser(authentication)).thenReturn(admin);
        when(userAccessService.isAdmin(admin)).thenReturn(true);
        when(teamRepository.findAll()).thenReturn(List.of(team));

        TeamService teamService = createService();
        List<TeamResponse> teams = teamService.getMyTeams(authentication);

        assertThat(teams).hasSize(1);
        assertThat(teams.get(0).name()).isEqualTo("Arsenal");
    }

    @Test
    void getTeam_sortsPlayersAndCoachesByName() {
        UUID teamId = UUID.randomUUID();
        Team team = team(teamId, "Sorting FC");

        PlayerProfile playerZulu = profile(UUID.randomUUID(), "Zara", "Zulu", null);
        PlayerProfile playerMason = profile(UUID.randomUUID(), "Mia", "Mason", null);
        PlayerProfile playerAdams = profile(UUID.randomUUID(), "Adam", "Adams", player(UUID.randomUUID(), "adam@test.com"));

        Coach coachBrown = coach(UUID.randomUUID(), "Brian", "Brown", "brian@test.com");
        Coach coachAnderson = coach(UUID.randomUUID(), "Anna", "Anderson", "anna@test.com");
        team.getCoaches().add(coachBrown);
        team.getCoaches().add(coachAnderson);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerProfileRepository.findByTeam_Id(teamId)).thenReturn(List.of(playerZulu, playerMason, playerAdams));

        TeamService teamService = createService();
        TeamResponse response = teamService.getTeam(teamId, coach(UUID.randomUUID(), "coach@test.com"));

        assertThat(response.players()).extracting(TeamResponse.MemberInfo::lastName)
                .containsExactly("Adams", "Mason", "Zulu");
        assertThat(response.players()).extracting(TeamResponse.MemberInfo::linkedUserId)
                .containsExactly(playerAdams.getLinkedUser().getId(), null, null);
        assertThat(response.coaches()).extracting(TeamResponse.MemberInfo::lastName)
                .containsExactly("Anderson", "Brown");
    }

    @Test
    void getTeam_omitsContactDetailsFromCoach() {
        UUID teamId = UUID.randomUUID();
        Team team = team(teamId, "Private FC");
        Player linked = player(UUID.randomUUID(), "adam@test.com");
        PlayerProfile profile = profile(UUID.randomUUID(), "Adam", "Adams", linked);
        profile.setDateOfBirth(LocalDate.of(2014, 5, 2));
        team.getCoaches().add(coach(UUID.randomUUID(), "Anna", "Anderson", "anna@test.com"));

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerProfileRepository.findByTeam_Id(teamId)).thenReturn(List.of(profile));

        Coach actor = coach(UUID.randomUUID(), "coach@test.com");
        actor.setRole(UserRole.COACH);

        TeamResponse response = createService().getTeam(teamId, actor);

        assertThat(response.players()).singleElement().satisfies(member -> {
            assertThat(member.email()).isNull();
            assertThat(member.dateOfBirth()).isNull();
            assertThat(member.linkedUserId()).isEqualTo(linked.getId());
        });
        assertThat(response.coaches()).singleElement().satisfies(member -> assertThat(member.email()).isNull());
        assertThat(response.fans()).isEmpty();
        assertThat(response.parents()).isEmpty();
        verify(fanRepository, never()).findAllByTeams_Id(any());
        verify(userRepository, never()).findParentsOnTeam(any());
    }

    @Test
    void getTeam_includesContactDetailsForAdmin() {
        UUID teamId = UUID.randomUUID();
        Team team = team(teamId, "Private FC");
        Player linked = player(UUID.randomUUID(), "adam@test.com");
        PlayerProfile profile = profile(UUID.randomUUID(), "Adam", "Adams", linked);
        profile.setDateOfBirth(LocalDate.of(2014, 5, 2));
        team.getCoaches().add(coach(UUID.randomUUID(), "Anna", "Anderson", "anna@test.com"));
        Fan follower = fan(UUID.randomUUID(), "fan@test.com");
        Parent parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setFirstName("Pia");
        parent.setLastName("Parent");
        parent.setEmail("parent@test.com");

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerProfileRepository.findByTeam_Id(teamId)).thenReturn(List.of(profile));
        when(fanRepository.findAllByTeams_Id(teamId)).thenReturn(List.of(follower));
        when(userRepository.findParentsOnTeam(teamId)).thenReturn(List.of(parent));

        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        admin.setRole(UserRole.ADMIN);
        when(userAccessService.isAdmin(admin)).thenReturn(true);

        TeamResponse response = createService().getTeam(teamId, admin);

        assertThat(response.players()).singleElement().satisfies(member -> {
            assertThat(member.email()).isEqualTo("adam@test.com");
            assertThat(member.dateOfBirth()).isEqualTo(LocalDate.of(2014, 5, 2));
        });
        assertThat(response.coaches()).singleElement().satisfies(member -> assertThat(member.email()).isEqualTo("anna@test.com"));
        assertThat(response.fans()).extracting(TeamResponse.MemberInfo::email).containsExactly("fan@test.com");
        assertThat(response.parents()).extracting(TeamResponse.MemberInfo::email).containsExactly("parent@test.com");
    }

    @Test
    void getMyTeams_rejectsMissingAuthentication() {
        when(userAccessService.resolveCurrentUser(null))
                .thenThrow(new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required to access this resource."));
        TeamService teamService = createService();

        assertThatThrownBy(() -> teamService.getMyTeams(null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void getMyTeams_rejectsUnsupportedRole() {
        Jwt authentication = jwtWithRole(UUID.randomUUID().toString(), "coach@test.com", "MANAGER");
        when(userAccessService.resolveCurrentUser(authentication))
                .thenThrow(new BadRequestException("error.auth.role_missing", new Object[0], "Unsupported role."));

        TeamService teamService = createService();

        assertThatThrownBy(() -> teamService.getMyTeams(authentication))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getMyTeams_rejectsCoachWhenNoAccountFound() {
        String subject = UUID.randomUUID().toString();
        Jwt authentication = jwtWithRole(subject, "coach@test.com", "COACH");

        when(userAccessService.resolveCurrentUser(authentication))
                .thenThrow(new NotFoundException("error.user.not_found", new Object[]{"coach@test.com"}, "User not found"));

        TeamService teamService = createService();

        assertThatThrownBy(() -> teamService.getMyTeams(authentication))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getMyTeams_rejectsPlayerWhenNoAccountFound() {
        UUID playerId = UUID.randomUUID();
        Jwt authentication = jwtWithRole(playerId.toString(), "player@test.com", "PLAYER");

        when(userAccessService.resolveCurrentUser(authentication))
                .thenThrow(new NotFoundException("error.user.not_found", new Object[]{"player@test.com"}, "User not found"));

        TeamService teamService = createService();

        assertThatThrownBy(() -> teamService.getMyTeams(authentication))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getMyTeams_rejectsFanWhenNoAccountFound() {
        Jwt authentication = jwtWithRole("", "fan@test.com", "FAN");

        when(userAccessService.resolveCurrentUser(authentication))
                .thenThrow(new NotFoundException("error.user.not_found", new Object[]{"fan@test.com"}, "User not found"));

        TeamService teamService = createService();

        assertThatThrownBy(() -> teamService.getMyTeams(authentication))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getMyTeams_rejectsMissingEmailClaim() {
        Jwt authentication = jwtWithoutEmail(UUID.randomUUID().toString());
        when(userAccessService.resolveCurrentUser(authentication))
                .thenThrow(new BadRequestException("error.auth.email_missing", new Object[0], "Authenticated token does not contain an email."));

        TeamService teamService = createService();

        assertThatThrownBy(() -> teamService.getMyTeams(authentication))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createTeam_allowsAdminWithoutCoachAccount() {
        CreateTeamRequest request = new CreateTeamRequest("Real Madrid", "RMA", "https://example.com/logo.png");
        UUID adminId = UUID.randomUUID();
        Jwt adminJwt = jwtWithRole(adminId.toString(), "admin@test.com", "ADMIN");
        Admin admin = new Admin();
        admin.setId(adminId);
        admin.setRole(UserRole.ADMIN);
        when(userAccessService.resolveCurrentUser(adminJwt)).thenReturn(admin);
        when(userAccessService.isAdmin(admin)).thenReturn(true);

        when(teamRepository.existsByName("Real Madrid")).thenReturn(false);
        when(clubRepository.save(any(Club.class))).thenAnswer(invocation -> {
            Club saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> {
            Team saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        TeamService teamService = createService();
        TeamResponse response = teamService.createTeam(request, adminJwt);

        assertThat(response.name()).isEqualTo("Real Madrid");
        verify(coachRepository, never()).save(any(Coach.class));
    }

    @Test
    void createTeam_rejectsCoachWithoutClub() {
        UUID coachId = UUID.randomUUID();
        CreateTeamRequest request = new CreateTeamRequest("Arsenal", "ARS", "https://example.com/arsenal.png");
        Jwt coachJwt = jwtWithRole(coachId.toString(), "coach@test.com", "COACH");
        Coach coach = coach(coachId, "coach@test.com");
        coach.setRole(UserRole.COACH);
        when(teamRepository.existsByName("Arsenal")).thenReturn(false);
        when(userAccessService.resolveCurrentUser(coachJwt)).thenReturn(coach);

        TeamService teamService = createService();

        assertThatThrownBy(() -> teamService.createTeam(request, coachJwt))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getMessageCode())
                .isEqualTo("error.club.required");
        verify(clubRepository, never()).save(any(Club.class));
        verify(coachRepository, never()).save(any(Coach.class));
    }

    @Test
    void deleteTeam_removesRelatedDataAndArtifacts() {
        UUID teamId = UUID.randomUUID();
        Team team = team(teamId, "Red Devils");

        Coach coach = coach(UUID.randomUUID(), "coach@test.com");
        coach.addTeam(team);

        Fan fan = fan(UUID.randomUUID(), "fan@test.com");
        fan.addTeam(team);

        Match match = match(UUID.randomUUID(), team, null);
        Clip clip = clip(UUID.randomUUID(), match);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(matchRepository.findAllByHomeTeam_IdOrAwayTeam_Id(teamId, teamId)).thenReturn(List.of(match));
        when(clipRepository.findAllByMatch_IdIn(List.of(match.getId()))).thenReturn(List.of(clip));

        Coach actor = coach(UUID.randomUUID(), "admin@test.com");
        actor.setRole(UserRole.COACH);

        TeamService teamService = createService();
        teamService.deleteTeam(teamId, actor);

        verify(teamRosterRepository).deleteByTeam_Id(teamId);
        verify(teamInviteRepository).deleteAllByTeam_Id(teamId);
        verify(matchSquadMemberRepository).deleteAllByTeam_Id(teamId);
        verify(clipRepository).deleteAllByMatch_IdIn(List.of(match.getId()));
        verify(matchRepository).deleteAll(List.of(match));
        verify(teamRepository).delete(team);
        verify(teamRepository).flush();
        verify(minioObjectCleanupService).deleteMatchArtifactsForTeamDeletion(List.of(match), List.of(clip));
        verify(auditEventService).record(
            org.mockito.ArgumentMatchers.eq("TEAM_DELETED"),
            org.mockito.ArgumentMatchers.eq(actor.getId()),
            org.mockito.ArgumentMatchers.eq("COACH"),
            org.mockito.ArgumentMatchers.eq("TEAM"),
            org.mockito.ArgumentMatchers.eq(teamId.toString()),
            org.mockito.ArgumentMatchers.contains("matchIds=[" + match.getId() + "], clipIds=[" + clip.getId() + "]")
        );
    }

    @Test
    void addPlayerToTeam_recordsAuditForManualAdd() {
        UUID teamId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Team team = team(teamId, "Arsenal");
        team.setClub(club());
        Player player = player(playerId, "player@test.com");
        Admin admin = new Admin();
        admin.setId(adminId);
        admin.setRole(UserRole.ADMIN);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(player));
        when(playerProfileRepository.findByLinkedUser_Id(playerId)).thenReturn(Optional.empty());
        when(playerProfileRepository.save(any(PlayerProfile.class))).thenAnswer(invocation -> {
            PlayerProfile saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        createService().addPlayerToTeam(teamId, playerId, admin);

        ArgumentCaptor<PlayerProfile> saved = ArgumentCaptor.forClass(PlayerProfile.class);
        verify(playerProfileRepository).save(saved.capture());
        assertThat(saved.getValue().getLinkedUser()).isEqualTo(player);
        verify(teamRosterRepository).save(any(TeamRoster.class));
        verify(auditEventService).record(
                org.mockito.ArgumentMatchers.eq("TEAM_PLAYER_ADDED"),
                org.mockito.ArgumentMatchers.eq(adminId),
                org.mockito.ArgumentMatchers.eq("ADMIN"),
                org.mockito.ArgumentMatchers.eq("TEAM"),
                org.mockito.ArgumentMatchers.eq(teamId.toString()),
                org.mockito.ArgumentMatchers.eq("playerId=" + playerId)
        );
    }

    @Test
    void addPlayerToTeam_addsSecondTeamInTheSameClub() {
        UUID teamId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Club club = club();
        Team team = team(teamId, "U13");
        team.setClub(club);
        Player player = player(playerId, "player@test.com");
        PlayerProfile profile = profile(UUID.randomUUID(), "Pat", "Player", player);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(player));
        when(playerProfileRepository.findByLinkedUser_Id(playerId)).thenReturn(Optional.of(profile));

        createService().addPlayerToTeam(teamId, playerId, null);

        verify(teamRosterRepository).save(any(TeamRoster.class));
        verify(playerProfileRepository, never()).save(any());
    }

    @Test
    void addPlayerToTeam_addsTeamFromAnotherClub() {
        UUID teamId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Team team = team(teamId, "Fradi U11");
        team.setClub(club());
        Player player = player(playerId, "player@test.com");
        PlayerProfile profile = profile(UUID.randomUUID(), "Pat", "Player", player);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(player));
        when(playerProfileRepository.findByLinkedUser_Id(playerId)).thenReturn(Optional.of(profile));

        createService().addPlayerToTeam(teamId, playerId, null);

        verify(teamRosterRepository).save(any(TeamRoster.class));
        verify(playerProfileRepository, never()).save(any());
    }

    @Test
    void createPlayerProfile_createsClubWhenMissing() {
        UUID teamId = UUID.randomUUID();
        Team team = team(teamId, "Arsenal");
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(clubRepository.save(any(Club.class))).thenAnswer(invocation -> {
            Club saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(teamRepository.save(team)).thenReturn(team);
        when(playerProfileRepository.save(any(PlayerProfile.class))).thenAnswer(invocation -> {
            PlayerProfile saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        createService().createPlayerProfile(
                teamId, new CreatePlayerProfileRequest("Ann", "Adams", null), null);

        assertThat(team.getClub()).isNotNull();
        assertThat(team.getClub().getName()).isEqualTo("Arsenal");
        verify(playerProfileRepository).save(any(PlayerProfile.class));
    }

    @Test
    void addRosterProfile_createsClubWhenMissing() {
        UUID teamId = UUID.randomUUID();
        Team team = team(teamId, "U11");
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(clubRepository.save(any(Club.class))).thenAnswer(invocation -> {
            Club saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(teamRepository.save(team)).thenReturn(team);
        when(playerProfileRepository.save(any(PlayerProfile.class))).thenAnswer(invocation -> {
            PlayerProfile saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        PlayerProfile profile = createService().addRosterProfile(teamId, "Bela", "Teszt", java.time.LocalDate.of(2014, 5, 1));

        assertThat(team.getClub()).isNotNull();
        assertThat(profile.getFirstName()).isEqualTo("Bela");
        assertThat(profile.getLastName()).isEqualTo("Teszt");
    }

    @Test
    void createPlayerProfile_savesUnlinkedProfileOnTeam() {
        UUID teamId = UUID.randomUUID();
        Team team = team(teamId, "Arsenal");
        team.setClub(club());
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerProfileRepository.save(any(PlayerProfile.class))).thenAnswer(invocation -> {
            PlayerProfile saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        createService().createPlayerProfile(teamId, new CreatePlayerProfileRequest("Ann", "Adams", null), null);

        ArgumentCaptor<PlayerProfile> saved = ArgumentCaptor.forClass(PlayerProfile.class);
        verify(playerProfileRepository).save(saved.capture());
        assertThat(saved.getValue().getLinkedUser()).isNull();
        assertThat(saved.getValue().getFirstName()).isEqualTo("Ann");
        verify(teamRosterRepository).save(any(TeamRoster.class));
    }

    @Test
    void removePlayerFromTeam_clearsTeamAndKeepsProfile() {
        UUID teamId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Team team = team(teamId, "Arsenal");
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerProfileRepository.existsById(profileId)).thenReturn(true);
        when(teamRosterRepository.existsById(new TeamRosterId(profileId, teamId))).thenReturn(true);

        createService().removePlayerFromTeam(teamId, profileId, null);

        verify(teamRosterRepository).deleteById(new TeamRosterId(profileId, teamId));
        verify(playerProfileRepository, never()).delete(any());
    }

    @Test
    void removeParentFromTeam_unlinksWhenChildOnlyOnThisTeam() {
        UUID teamId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        PlayerProfile child = profile(profileId, "Ann", "Adams", player(UUID.randomUUID(), "ann@test.com"));
        PlayerParent link = parentLink(parentId, child);

        when(playerParentRepository.findLinksForParentOnTeam(parentId, teamId)).thenReturn(List.of(link));
        when(teamRosterRepository.existsByPlayerProfile_IdAndTeam_IdNot(profileId, teamId)).thenReturn(false);

        createService().removeParentFromTeam(teamId, parentId, null);

        verify(playerParentRepository).deleteAll(List.of(link));
    }

    @Test
    void removeParentFromTeam_keepsLinkWhenChildOnOtherTeam() {
        UUID teamId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        PlayerProfile child = profile(profileId, "Ann", "Adams", player(UUID.randomUUID(), "ann@test.com"));
        PlayerParent link = parentLink(parentId, child);

        when(playerParentRepository.findLinksForParentOnTeam(parentId, teamId)).thenReturn(List.of(link));
        when(teamRosterRepository.existsByPlayerProfile_IdAndTeam_IdNot(profileId, teamId)).thenReturn(true);

        assertThatThrownBy(() -> createService().removeParentFromTeam(teamId, parentId, null))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getMessageCode())
                .isEqualTo("error.player_parent.other_teams");

        verify(playerParentRepository, never()).deleteAll(any());
    }

    @Test
    void removeParentFromTeam_rejectsLastParentWithoutLogin() {
        UUID teamId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        PlayerProfile child = profile(profileId, "Ann", "Adams", null);
        PlayerParent link = parentLink(parentId, child);

        when(playerParentRepository.findLinksForParentOnTeam(parentId, teamId)).thenReturn(List.of(link));
        when(teamRosterRepository.existsByPlayerProfile_IdAndTeam_IdNot(profileId, teamId)).thenReturn(false);
        when(playerParentRepository.findByPlayerProfile_Id(profileId)).thenReturn(List.of(link));

        assertThatThrownBy(() -> createService().removeParentFromTeam(teamId, parentId, null))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getMessageCode())
                .isEqualTo("error.player_parent.last_without_login");

        verify(playerParentRepository, never()).deleteAll(any());
    }

    @Test
    void deleteTeam_throwsWhenMissing() {
        UUID teamId = UUID.randomUUID();
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        User actor = org.mockito.Mockito.mock(User.class);

        TeamService teamService = createService();

        assertThatThrownBy(() -> teamService.deleteTeam(teamId, actor))
                .isInstanceOf(NotFoundException.class);

        verify(matchRepository, never()).findAllByHomeTeam_IdOrAwayTeam_Id(any(), any());
    }

    private Jwt jwt(String subject, String email) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("email", email)
                .claim("preferred_username", email)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private Jwt jwtWithoutEmail(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private Jwt jwtWithRole(String subject, String email, String role) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("email", email)
                .claim("preferred_username", email)
                .claim("realm_access", Map.of("roles", List.of(role.toLowerCase())))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private Club club() {
        Club club = new Club();
        club.setId(UUID.randomUUID());
        club.setName("Club");
        return club;
    }

    private PlayerProfile profile(UUID id, String firstName, String lastName, Player linkedUser) {
        PlayerProfile profile = new PlayerProfile();
        profile.setId(id);
        profile.setFirstName(firstName);
        profile.setLastName(lastName);
        profile.setLinkedUser(linkedUser);
        return profile;
    }

    private PlayerParent parentLink(UUID parentId, PlayerProfile profile) {
        Parent parent = new Parent();
        parent.setId(parentId);
        PlayerParent link = new PlayerParent();
        link.setId(new PlayerParentId(parentId, profile.getId()));
        link.setUser(parent);
        link.setPlayerProfile(profile);
        return link;
    }

    private Team team(String name) {
        return team(UUID.randomUUID(), name);
    }

    private Team team(UUID id, String name) {
        Team team = new Team();
        team.setId(id);
        team.setName(name);
        return team;
    }

    private Match match(UUID id, Team homeTeam, Team awayTeam) {
        Match match = new Match();
        match.setId(id);
        match.setHomeTeam(homeTeam);
        match.setAwayTeam(awayTeam);
        return match;
    }

    private Clip clip(UUID id, Match match) {
        Clip clip = new Clip();
        clip.setId(id);
        clip.setMatch(match);
        return clip;
    }

    private Coach coach(UUID id, String email) {
        Coach coach = new Coach();
        coach.setId(id);
        coach.setEmail(email);
        coach.setFirstName("Coach");
        coach.setLastName("One");
        return coach;
    }

    private Coach coach(UUID id, String firstName, String lastName, String email) {
        Coach coach = new Coach();
        coach.setId(id);
        coach.setEmail(email);
        coach.setFirstName(firstName);
        coach.setLastName(lastName);
        return coach;
    }

    private Coach coach(UUID id, String keycloakId, String email) {
        return coach(id, email);
    }

    private Player player(UUID id, String email) {
        Player player = new Player();
        player.setId(id);
        player.setEmail(email);
        player.setFirstName("Player");
        player.setLastName("One");
        return player;
    }

    private Player player(UUID id, String firstName, String lastName, String email) {
        Player player = new Player();
        player.setId(id);
        player.setEmail(email);
        player.setFirstName(firstName);
        player.setLastName(lastName);
        return player;
    }

    private Player player(UUID id, String keycloakId, String email) {
        return player(id, email);
    }

    private Fan fan(UUID id, String email) {
        Fan fan = new Fan();
        fan.setId(id);
        fan.setEmail(email);
        fan.setFirstName("Fan");
        fan.setLastName("One");
        return fan;
    }

    private Fan fan(UUID id, String keycloakId, String email) {
        return fan(id, email);
    }
}