package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.PlayerParent;
import com.example.footballanalysis.model.db.PlayerParentId;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.InviteKind;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.TeamInvite;
import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.user.Admin;
import com.example.footballanalysis.model.db.user.ClubAdmin;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Fan;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.CreatePlayerProfileRequest;
import com.example.footballanalysis.model.requests.CreateTeamRequest;
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
import com.example.footballanalysis.model.db.TeamRoster;
import com.example.footballanalysis.model.db.TeamRosterId;
import com.example.footballanalysis.repository.TeamInviteRepository;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.TeamRosterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

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
    private TeamInviteRepository teamInviteRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private CupTeamRepository cupTeamRepository;

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

    @Mock
    private TeamMembershipQuery teamMembershipQuery;

    @Mock
    private ExtraHatService extraHatService;

    private TeamService createService() {
        return new TeamService(
                teamRepository,
                clubRepository,
                playerRepository,
                playerProfileRepository,
                teamRosterRepository,
                playerParentRepository,
                teamInviteRepository,
                matchRepository,
                cupTeamRepository,
                clipRepository,
                matchSquadMemberRepository,
                userRepository,
                minioObjectCleanupService,
                auditEventService,
                userAccessService,
                teamMembershipQuery,
                extraHatService);
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
        when(userAccessService.getAccessibleTeamIds(coach, UserRole.COACH)).thenReturn(Set.of(team.getId()));
        when(teamRepository.findAllById(any())).thenReturn(List.of(team));

        TeamService teamService = createService();
        List<TeamResponse> teams = teamService.getMyTeams(authentication, UserRole.COACH);

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
        when(userAccessService.getAccessibleTeamIds(player, UserRole.PLAYER)).thenReturn(Set.of(team.getId()));
        when(teamRepository.findAllById(any())).thenReturn(List.of(team));

        TeamService teamService = createService();
        List<TeamResponse> teams = teamService.getMyTeams(authentication, UserRole.PLAYER);

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
        when(userAccessService.getAccessibleTeamIds(fan, UserRole.FAN)).thenReturn(Set.of(team.getId()));
        when(teamRepository.findAllById(any())).thenReturn(List.of(team));

        TeamService teamService = createService();
        List<TeamResponse> teams = teamService.getMyTeams(authentication, UserRole.FAN);

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
        when(userAccessService.getAccessibleTeamIds(admin, UserRole.ADMIN)).thenReturn(Set.of(team.getId()));
        when(teamRepository.findAllById(any())).thenReturn(List.of(team));

        TeamService teamService = createService();
        List<TeamResponse> teams = teamService.getMyTeams(authentication, UserRole.ADMIN);

        assertThat(teams).hasSize(1);
        assertThat(teams.get(0).name()).isEqualTo("Arsenal");
    }

    @Test
    void listCatalog_searchesWithinTheRoleAndReturnsCounts() {
        UUID teamId = UUID.randomUUID();
        Team team = team(teamId, "Academy U19");
        Club club = new Club();
        club.setId(UUID.randomUUID());
        club.setName("Academy");
        team.setClub(club);
        Coach coach = coach(UUID.randomUUID(), "coach@test.com");
        coach.setRole(UserRole.COACH);
        Jwt authentication = jwtWithRole(coach.getId().toString(), "coach@test.com", "COACH");

        when(userAccessService.resolveCurrentUser(authentication)).thenReturn(coach);
        when(userAccessService.getAccessibleTeamIds(coach, UserRole.COACH)).thenReturn(Set.of(teamId));
        when(teamRepository.countCatalogByIds(any(), eq("%u19%"))).thenReturn(1L);
        when(teamRepository.findCatalogByIds(any(), eq("%u19%"), any())).thenReturn(List.of(team));
        when(teamRosterRepository.countByTeamIds(any())).thenReturn(List.<Object[]>of(new Object[]{teamId, 4L}));
        when(teamMembershipQuery.countCoachesByTeamIds(any())).thenReturn(Map.of(teamId, 1L));

        TeamCatalogResponse response = createService().listCatalog(authentication, UserRole.COACH, " U19 ");

        assertThat(response.partial()).isFalse();
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("Academy U19");
            assertThat(item.clubName()).isEqualTo("Academy");
            assertThat(item.playerCount()).isEqualTo(4);
            assertThat(item.coachCount()).isEqualTo(1);
        });
        verify(teamRepository, never()).findCatalog(any(), any());
    }

    @Test
    void listCatalog_adminBlankQueryReadsEveryTeamWithoutARoster() {
        UUID teamId = UUID.randomUUID();
        Team team = team(teamId, "Arsenal");
        Jwt authentication = jwtWithRole(UUID.randomUUID().toString(), "admin@test.com", "ADMIN");
        Admin admin = new Admin();
        admin.setId(UUID.fromString(authentication.getSubject()));
        admin.setRole(UserRole.ADMIN);

        when(userAccessService.resolveCurrentUser(authentication)).thenReturn(admin);
        when(userAccessService.hasHat(admin, UserRole.ADMIN)).thenReturn(true);
        when(teamRepository.count()).thenReturn(1L);
        when(teamRepository.findCatalog(isNull(), any())).thenReturn(List.of(team));
        when(teamRosterRepository.countByTeamIds(any())).thenReturn(List.<Object[]>of());
        when(teamMembershipQuery.countCoachesByTeamIds(any())).thenReturn(Map.of());

        TeamCatalogResponse response = createService().listCatalog(authentication, UserRole.ADMIN, "  ");

        assertThat(response.partial()).isFalse();
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("Arsenal");
            assertThat(item.playerCount()).isZero();
            assertThat(item.coachCount()).isZero();
        });
        verify(teamRepository, never()).findCatalogByIds(any(), any(), any());
        verify(userAccessService, never()).getAccessibleTeamIds(any(), any());
    }

    @Test
    void listCatalog_adminManyTeamsStayHiddenWithoutLoadingIds() {
        Jwt authentication = jwtWithRole(UUID.randomUUID().toString(), "admin@test.com", "ADMIN");
        Admin admin = new Admin();
        admin.setId(UUID.fromString(authentication.getSubject()));
        admin.setRole(UserRole.ADMIN);
        when(userAccessService.resolveCurrentUser(authentication)).thenReturn(admin);
        when(userAccessService.hasHat(admin, UserRole.ADMIN)).thenReturn(true);
        when(teamRepository.count()).thenReturn((long) TeamService.COMPLETE_LIST_LIMIT + 1);

        TeamCatalogResponse response = createService().listCatalog(authentication, UserRole.ADMIN, "");

        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isEqualTo(TeamService.COMPLETE_LIST_LIMIT + 1L);
        assertThat(response.partial()).isTrue();
        verify(teamRepository, never()).findCatalog(any(), any());
        verify(userAccessService, never()).getAccessibleTeamIds(any(), any());
    }

    @Test
    void listCatalog_browseReturnsTheRequestedPage() {
        UUID teamId = UUID.randomUUID();
        Team team = team(teamId, "Academy");
        Jwt authentication = jwtWithRole(UUID.randomUUID().toString(), "admin@test.com", "ADMIN");
        Admin admin = new Admin();
        admin.setId(UUID.fromString(authentication.getSubject()));
        admin.setRole(UserRole.ADMIN);
        when(userAccessService.resolveCurrentUser(authentication)).thenReturn(admin);
        when(userAccessService.hasHat(admin, UserRole.ADMIN)).thenReturn(true);
        when(teamRepository.count()).thenReturn(45L);
        when(teamRepository.findCatalog(isNull(), any())).thenReturn(List.of(team));
        when(teamRosterRepository.countByTeamIds(any())).thenReturn(List.<Object[]>of());
        when(teamMembershipQuery.countCoachesByTeamIds(any())).thenReturn(Map.of());

        TeamCatalogResponse response = createService().listCatalog(authentication, UserRole.ADMIN, "", 2, 20);

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.total()).isEqualTo(45);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.partial()).isTrue();
        assertThat(response.items()).hasSize(1);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(teamRepository).findCatalog(isNull(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        verify(userAccessService, never()).getAccessibleTeamIds(any(), any());
    }

    @Test
    void listCatalog_browsePastTheEndDoesNotLoadRows() {
        Jwt authentication = jwtWithRole(UUID.randomUUID().toString(), "admin@test.com", "ADMIN");
        Admin admin = new Admin();
        admin.setId(UUID.fromString(authentication.getSubject()));
        admin.setRole(UserRole.ADMIN);
        when(userAccessService.resolveCurrentUser(authentication)).thenReturn(admin);
        when(userAccessService.hasHat(admin, UserRole.ADMIN)).thenReturn(true);
        when(teamRepository.count()).thenReturn(45L);

        TeamCatalogResponse response = createService().listCatalog(authentication, UserRole.ADMIN, "  ", 4, 20);

        assertThat(response.items()).isEmpty();
        assertThat(response.page()).isEqualTo(4);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.partial()).isFalse();
        verify(teamRepository, never()).findCatalog(any(), any());
    }

    @Test
    void listCatalog_adminRoleWithoutTheHatDoesNotReadEveryTeam() {
        Coach coach = coach(UUID.randomUUID(), "coach@test.com");
        coach.setRole(UserRole.COACH);
        Jwt authentication = jwtWithRole(coach.getId().toString(), "coach@test.com", "COACH");
        when(userAccessService.resolveCurrentUser(authentication)).thenReturn(coach);
        when(userAccessService.hasHat(coach, UserRole.ADMIN)).thenReturn(false);

        TeamCatalogResponse response = createService().listCatalog(authentication, UserRole.ADMIN, "u19", 1, 20);

        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isZero();
        assertThat(response.partial()).isFalse();
        verify(teamRepository, never()).count();
        verify(teamRepository, never()).countCatalog(any());
        verify(teamRepository, never()).findCatalog(any(), any());
        verify(userAccessService, never()).getAccessibleTeamIds(any(), any());
    }

    @Test
    void listCatalog_manyTeamsStayHiddenUntilThereIsAQuery() {
        Coach coach = coach(UUID.randomUUID(), "coach@test.com");
        Jwt authentication = jwtWithRole(coach.getId().toString(), "coach@test.com", "COACH");
        Set<UUID> teamIds = new java.util.LinkedHashSet<>();
        for (int i = 0; i < TeamService.COMPLETE_LIST_LIMIT + 1; i++) {
            teamIds.add(UUID.randomUUID());
        }
        when(userAccessService.resolveCurrentUser(authentication)).thenReturn(coach);
        when(userAccessService.getAccessibleTeamIds(coach, UserRole.COACH)).thenReturn(teamIds);

        TeamCatalogResponse response = createService().listCatalog(authentication, UserRole.COACH, "  ");

        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isEqualTo(teamIds.size());
        assertThat(response.partial()).isTrue();
        verify(teamRepository, never()).findCatalog(any(), any());
        verify(teamRepository, never()).findCatalogByIds(any(), any(), any());
    }

    @Test
    void listCatalog_searchStopsAfterTheResultLimit() {
        UUID teamId = UUID.randomUUID();
        Team team = team(teamId, "Academy");
        Coach coach = coach(UUID.randomUUID(), "coach@test.com");
        Jwt authentication = jwtWithRole(coach.getId().toString(), "coach@test.com", "COACH");
        when(userAccessService.resolveCurrentUser(authentication)).thenReturn(coach);
        when(userAccessService.getAccessibleTeamIds(coach, UserRole.COACH)).thenReturn(Set.of(teamId));
        when(teamRepository.countCatalogByIds(any(), eq("%a%"))).thenReturn(25L);
        when(teamRepository.findCatalogByIds(any(), eq("%a%"), any())).thenReturn(List.of(team));
        when(teamRosterRepository.countByTeamIds(any())).thenReturn(List.<Object[]>of());
        when(teamMembershipQuery.countCoachesByTeamIds(any())).thenReturn(Map.of());

        TeamCatalogResponse response = createService().listCatalog(authentication, UserRole.COACH, "a");

        assertThat(response.total()).isEqualTo(25);
        assertThat(response.partial()).isTrue();
        assertThat(response.items()).hasSize(1);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(teamRepository).findCatalogByIds(any(), eq("%a%"), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(TeamService.SEARCH_RESULT_LIMIT);
    }

    @Test
    void listCatalog_emptyAccessDoesNotQuery() {
        Coach coach = coach(UUID.randomUUID(), "coach@test.com");
        Jwt authentication = jwtWithRole(coach.getId().toString(), "coach@test.com", "COACH");
        when(userAccessService.resolveCurrentUser(authentication)).thenReturn(coach);
        when(userAccessService.getAccessibleTeamIds(coach, UserRole.COACH)).thenReturn(Set.of());

        TeamCatalogResponse response = createService().listCatalog(authentication, UserRole.COACH, "u19");

        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isZero();
        verify(teamRepository, never()).findCatalog(any(), any());
        verify(teamRepository, never()).findCatalogByIds(any(), any(), any());
    }

    @Test
    void listCatalog_escapesLikeWildcards() {
        UUID teamId = UUID.randomUUID();
        Coach coach = coach(UUID.randomUUID(), "coach@test.com");
        Jwt authentication = jwtWithRole(coach.getId().toString(), "coach@test.com", "COACH");
        when(userAccessService.resolveCurrentUser(authentication)).thenReturn(coach);
        when(userAccessService.getAccessibleTeamIds(coach, UserRole.COACH)).thenReturn(Set.of(teamId));
        when(teamRepository.countCatalogByIds(any(), eq("%100\\%%"))).thenReturn(0L);

        TeamCatalogResponse response = createService().listCatalog(authentication, UserRole.COACH, "100%");

        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isZero();
        verify(teamRepository).countCatalogByIds(any(), eq("%100\\%%"));
        verify(teamRepository, never()).findCatalogByIds(any(), any(), any());
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

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerProfileRepository.findByTeam_Id(teamId)).thenReturn(List.of(playerZulu, playerMason, playerAdams));
        when(teamMembershipQuery.findCoachIdsByTeamId(teamId))
                .thenReturn(List.of(coachBrown.getId(), coachAnderson.getId()));
        when(userRepository.findAllById(List.of(coachBrown.getId(), coachAnderson.getId())))
                .thenReturn(List.of(coachBrown, coachAnderson));

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
        Coach anna = coach(UUID.randomUUID(), "Anna", "Anderson", "anna@test.com");

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerProfileRepository.findByTeam_Id(teamId)).thenReturn(List.of(profile));
        when(teamMembershipQuery.findCoachIdsByTeamId(teamId)).thenReturn(List.of(anna.getId()));
        when(userRepository.findAllById(List.of(anna.getId()))).thenReturn(List.of(anna));

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
        verify(teamMembershipQuery, never()).findFanIdsByTeamId(any());
        verify(userRepository, never()).findParentsOnTeam(any());
    }

    @Test
    void getTeam_includesContactDetailsForAdmin() {
        UUID teamId = UUID.randomUUID();
        Team team = team(teamId, "Private FC");
        Player linked = player(UUID.randomUUID(), "adam@test.com");
        PlayerProfile profile = profile(UUID.randomUUID(), "Adam", "Adams", linked);
        profile.setDateOfBirth(LocalDate.of(2014, 5, 2));
        Coach anna = coach(UUID.randomUUID(), "Anna", "Anderson", "anna@test.com");
        Fan follower = fan(UUID.randomUUID(), "fan@test.com");
        Parent parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setFirstName("Pia");
        parent.setLastName("Parent");
        parent.setEmail("parent@test.com");

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerProfileRepository.findByTeam_Id(teamId)).thenReturn(List.of(profile));
        when(teamMembershipQuery.findCoachIdsByTeamId(teamId)).thenReturn(List.of(anna.getId()));
        when(teamMembershipQuery.findFanIdsByTeamId(teamId)).thenReturn(List.of(follower.getId()));
        when(userRepository.findAllById(List.of(anna.getId()))).thenReturn(List.of(anna));
        when(userRepository.findAllById(List.of(follower.getId()))).thenReturn(List.of(follower));
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
    void getTeam_allowsClubAdminOfThatClub() {
        UUID teamId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        Club club = new Club();
        club.setId(clubId);
        Team team = team(teamId, "Club FC");
        team.setClub(club);
        ClubAdmin clubAdmin = new ClubAdmin();
        clubAdmin.setId(UUID.randomUUID());
        clubAdmin.setRole(UserRole.CLUB_ADMIN);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(userAccessService.isClubAdmin(clubAdmin, clubId)).thenReturn(true);
        when(playerProfileRepository.findByTeam_Id(teamId)).thenReturn(List.of());
        when(teamMembershipQuery.findCoachIdsByTeamId(teamId)).thenReturn(List.of());
        when(teamMembershipQuery.findFanIdsByTeamId(teamId)).thenReturn(List.of());
        when(userRepository.findParentsOnTeam(teamId)).thenReturn(List.of());

        TeamResponse response = createService().getTeam(teamId, clubAdmin);

        assertThat(response.name()).isEqualTo("Club FC");
        verify(userAccessService, never()).requireTeamAccess(any(), any());
    }

    @Test
    void getTeam_rejectsClubAdminOfAnotherClub() {
        UUID teamId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        Club club = new Club();
        club.setId(clubId);
        Team team = team(teamId, "Other FC");
        team.setClub(club);
        ClubAdmin clubAdmin = new ClubAdmin();
        clubAdmin.setId(UUID.randomUUID());
        clubAdmin.setRole(UserRole.CLUB_ADMIN);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(userAccessService.isClubAdmin(clubAdmin, clubId)).thenReturn(false);
        doThrow(new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied to team."))
                .when(userAccessService).requireTeamAccess(any(), any());

        assertThatThrownBy(() -> createService().getTeam(teamId, clubAdmin))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getMyTeams_rejectsMissingAuthentication() {
        when(userAccessService.resolveCurrentUser(null))
                .thenThrow(new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required to access this resource."));
        TeamService teamService = createService();

        assertThatThrownBy(() -> teamService.getMyTeams(null, UserRole.COACH))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void getMyTeams_rejectsUnsupportedRole() {
        Jwt authentication = jwtWithRole(UUID.randomUUID().toString(), "coach@test.com", "MANAGER");
        when(userAccessService.resolveCurrentUser(authentication))
                .thenThrow(new BadRequestException("error.auth.role_missing", new Object[0], "Unsupported role."));

        TeamService teamService = createService();

        assertThatThrownBy(() -> teamService.getMyTeams(authentication, UserRole.COACH))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getMyTeams_rejectsCoachWhenNoAccountFound() {
        String subject = UUID.randomUUID().toString();
        Jwt authentication = jwtWithRole(subject, "coach@test.com", "COACH");

        when(userAccessService.resolveCurrentUser(authentication))
                .thenThrow(new NotFoundException("error.user.not_found", new Object[]{"coach@test.com"}, "User not found"));

        TeamService teamService = createService();

        assertThatThrownBy(() -> teamService.getMyTeams(authentication, UserRole.COACH))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getMyTeams_rejectsPlayerWhenNoAccountFound() {
        UUID playerId = UUID.randomUUID();
        Jwt authentication = jwtWithRole(playerId.toString(), "player@test.com", "PLAYER");

        when(userAccessService.resolveCurrentUser(authentication))
                .thenThrow(new NotFoundException("error.user.not_found", new Object[]{"player@test.com"}, "User not found"));

        TeamService teamService = createService();

        assertThatThrownBy(() -> teamService.getMyTeams(authentication, UserRole.PLAYER))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getMyTeams_rejectsFanWhenNoAccountFound() {
        Jwt authentication = jwtWithRole("", "fan@test.com", "FAN");

        when(userAccessService.resolveCurrentUser(authentication))
                .thenThrow(new NotFoundException("error.user.not_found", new Object[]{"fan@test.com"}, "User not found"));

        TeamService teamService = createService();

        assertThatThrownBy(() -> teamService.getMyTeams(authentication, UserRole.FAN))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getMyTeams_rejectsMissingEmailClaim() {
        Jwt authentication = jwtWithoutEmail(UUID.randomUUID().toString());
        when(userAccessService.resolveCurrentUser(authentication))
                .thenThrow(new BadRequestException("error.auth.email_missing", new Object[0], "Authenticated token does not contain an email."));

        TeamService teamService = createService();

        assertThatThrownBy(() -> teamService.getMyTeams(authentication, UserRole.COACH))
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
        verify(teamMembershipQuery, never()).insertCoachTeam(any(), any());
    }

    @Test
    void createTeam_rejectsCoachWithoutClub() {
        UUID coachId = UUID.randomUUID();
        CreateTeamRequest request = new CreateTeamRequest("Arsenal", "ARS", "https://example.com/arsenal.png");
        Jwt coachJwt = jwtWithRole(coachId.toString(), "coach@test.com", "COACH");
        Coach coach = coach(coachId, "coach@test.com");
        coach.setRole(UserRole.COACH);
        when(userAccessService.resolveCurrentUser(coachJwt)).thenReturn(coach);

        TeamService teamService = createService();

        assertThatThrownBy(() -> teamService.createTeam(request, coachJwt))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getMessageCode())
                .isEqualTo("error.club.required");
        verify(clubRepository, never()).save(any(Club.class));
        verify(teamMembershipQuery, never()).insertCoachTeam(any(), any());
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
        verify(cupTeamRepository).clearRealTeamByTeamId(teamId);
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
    void deleteTeam_movesOpenChildLoginInviteToAnotherTeam() {
        UUID teamId = UUID.randomUUID();
        UUID otherTeamId = UUID.randomUUID();
        Team team = team(teamId, "Alpha");
        Team other = team(otherTeamId, "Beta");
        PlayerProfile child = profile(UUID.randomUUID(), "Ann", "Adams", null);
        TeamInvite invite = new TeamInvite();
        invite.setId(UUID.randomUUID());
        invite.setTeam(team);
        invite.setPlayerProfile(child);
        invite.setInviteKind(InviteKind.CHILD_LOGIN);
        invite.setUsedCount(0);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(matchRepository.findAllByHomeTeam_IdOrAwayTeam_Id(teamId, teamId)).thenReturn(List.of());
        when(teamInviteRepository.findByTeam_IdAndInviteKindAndUsedCount(teamId, InviteKind.CHILD_LOGIN, 0))
                .thenReturn(List.of(invite));
        when(playerProfileRepository.findTeamsByProfileId(child.getId())).thenReturn(List.of(other));

        createService().deleteTeam(teamId, null);

        assertThat(invite.getTeam()).isEqualTo(other);
        verify(teamInviteRepository).save(invite);
        verify(teamInviteRepository).deleteAllByTeam_Id(teamId);
    }

    @Test
    void deleteTeam_unlinksAwayMatchesAndKeepsThem() {
        UUID teamId = UUID.randomUUID();
        Team team = team(teamId, "Red Devils");
        Team opponent = team(UUID.randomUUID(), "Away FC");
        Match awayMatch = match(UUID.randomUUID(), opponent, team);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(matchRepository.findAllByHomeTeam_IdOrAwayTeam_Id(teamId, teamId)).thenReturn(List.of(awayMatch));

        createService().deleteTeam(teamId, null);

        assertThat(awayMatch.getAwayTeam()).isNull();
        verify(matchRepository).saveAll(List.of(awayMatch));
        verify(matchRepository, never()).deleteAll(any());
        verify(clipRepository, never()).deleteAllByMatch_IdIn(any());
        verify(cupTeamRepository).clearRealTeamByTeamId(teamId);
        verify(minioObjectCleanupService).deleteMatchArtifactsForTeamDeletion(List.of(), List.of());
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
        when(userRepository.findById(playerId)).thenReturn(Optional.of(player));
        when(playerProfileRepository.findByLinkedUser_Id(playerId)).thenReturn(Optional.empty());
        when(playerProfileRepository.save(any(PlayerProfile.class))).thenAnswer(invocation -> {
            PlayerProfile saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        when(userAccessService.isAdmin(admin)).thenReturn(true);

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
        when(userRepository.findById(playerId)).thenReturn(Optional.of(player));
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
        when(userRepository.findById(playerId)).thenReturn(Optional.of(player));
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
    void createPlayerProfile_rejectsCoach() {
        UUID teamId = UUID.randomUUID();
        Team team = team(teamId, "Arsenal");
        team.setClub(club());
        Coach coach = coach(UUID.randomUUID(), "coach@test.com");
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(userAccessService.isClubAdmin(coach, team.getClub().getId())).thenReturn(false);

        assertThatThrownBy(() -> createService().createPlayerProfile(
                teamId, new CreatePlayerProfileRequest("Ann", "Adams", null), coach))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.team.player.invite_only");

        verify(playerProfileRepository, never()).save(any());
    }

    @Test
    void addPlayerToTeam_rejectsCoach() {
        UUID teamId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Team team = team(teamId, "Arsenal");
        team.setClub(club());
        Coach coach = coach(UUID.randomUUID(), "coach@test.com");
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(userAccessService.isClubAdmin(coach, team.getClub().getId())).thenReturn(false);

        assertThatThrownBy(() -> createService().addPlayerToTeam(teamId, playerId, coach))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.team.player.invite_only");

        verify(userRepository, never()).findById(playerId);
    }

    @Test
    void removePlayerFromTeam_clearsTeamAndKeepsProfile() {
        UUID teamId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Team team = team(teamId, "Arsenal");
        PlayerProfile profile = profile(profileId, "Ann", "Adams", null);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(teamRosterRepository.existsById(new TeamRosterId(profileId, teamId))).thenReturn(true);

        createService().removePlayerFromTeam(teamId, profileId, null);

        verify(teamRosterRepository).deleteById(new TeamRosterId(profileId, teamId));
        verify(playerProfileRepository, never()).delete(any());
        verify(extraHatService, never()).revokeHat(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
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
    void deleteTeam_revokesHatsWhenNoOtherMembershipRemains() {
        UUID teamId = UUID.randomUUID();
        UUID coachId = UUID.randomUUID();
        UUID fanId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Team team = team(teamId, "Red Devils");
        PlayerProfile linked = profile(UUID.randomUUID(), "Ann", "Adams", player(playerId, "ann@test.com"));
        PlayerProfile unlinked = profile(UUID.randomUUID(), "Ben", "Bare", null);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(matchRepository.findAllByHomeTeam_IdOrAwayTeam_Id(teamId, teamId)).thenReturn(List.of());
        when(teamMembershipQuery.findCoachIdsByTeamId(teamId)).thenReturn(List.of(coachId));
        when(teamMembershipQuery.findFanIdsByTeamId(teamId)).thenReturn(List.of(fanId));
        when(playerProfileRepository.findByTeam_Id(teamId)).thenReturn(List.of(linked, unlinked));
        when(teamMembershipQuery.findCoachTeamIds(coachId)).thenReturn(List.of());
        when(teamMembershipQuery.findFanTeamIds(fanId)).thenReturn(List.of());
        when(playerProfileRepository.findTeamIdsByLinkedUserId(playerId)).thenReturn(List.of());

        createService().deleteTeam(teamId, null);

        verify(teamMembershipQuery).deleteCoachTeamsForTeam(teamId);
        verify(teamMembershipQuery).deleteFanTeamsForTeam(teamId);
        verify(extraHatService).revokeHat(coachId, UserRole.COACH);
        verify(extraHatService).revokeHat(fanId, UserRole.FAN);
        verify(extraHatService).revokeHat(playerId, UserRole.PLAYER);
    }

    @Test
    void deleteTeam_keepsHatsWhenAnotherTeamRemains() {
        UUID teamId = UUID.randomUUID();
        UUID coachId = UUID.randomUUID();
        UUID fanId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Team team = team(teamId, "Red Devils");
        PlayerProfile linked = profile(UUID.randomUUID(), "Ann", "Adams", player(playerId, "ann@test.com"));

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(matchRepository.findAllByHomeTeam_IdOrAwayTeam_Id(teamId, teamId)).thenReturn(List.of());
        when(teamMembershipQuery.findCoachIdsByTeamId(teamId)).thenReturn(List.of(coachId));
        when(teamMembershipQuery.findFanIdsByTeamId(teamId)).thenReturn(List.of(fanId));
        when(playerProfileRepository.findByTeam_Id(teamId)).thenReturn(List.of(linked));
        when(teamMembershipQuery.findCoachTeamIds(coachId)).thenReturn(List.of(UUID.randomUUID()));
        when(teamMembershipQuery.findFanTeamIds(fanId)).thenReturn(List.of(UUID.randomUUID()));
        when(playerProfileRepository.findTeamIdsByLinkedUserId(playerId)).thenReturn(List.of(UUID.randomUUID()));

        createService().deleteTeam(teamId, null);

        verify(extraHatService, never()).revokeHat(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void removeParentFromTeam_revokesHatWhenNoParentLinkRemains() {
        UUID teamId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        PlayerProfile child = profile(profileId, "Ann", "Adams", player(UUID.randomUUID(), "ann@test.com"));
        PlayerParent link = parentLink(parentId, child);

        when(playerParentRepository.findLinksForParentOnTeam(parentId, teamId)).thenReturn(List.of(link));
        when(teamRosterRepository.existsByPlayerProfile_IdAndTeam_IdNot(profileId, teamId)).thenReturn(false);
        when(playerParentRepository.findByUser_Id(parentId)).thenReturn(List.of());

        createService().removeParentFromTeam(teamId, parentId, null);

        verify(playerParentRepository).deleteAll(List.of(link));
        verify(extraHatService).revokeHat(parentId, UserRole.PARENT);
    }

    @Test
    void removeParentFromTeam_keepsHatWhenParentHasNoOtherRole() {
        UUID teamId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        PlayerProfile child = profile(profileId, "Ann", "Adams", player(UUID.randomUUID(), "ann@test.com"));
        PlayerParent link = parentLink(parentId, child);

        when(playerParentRepository.findLinksForParentOnTeam(parentId, teamId)).thenReturn(List.of(link));
        when(teamRosterRepository.existsByPlayerProfile_IdAndTeam_IdNot(profileId, teamId)).thenReturn(false);
        when(playerParentRepository.findByUser_Id(parentId)).thenReturn(List.of());
        when(userAccessService.isOnlyRemainingHat(parentId, UserRole.PARENT)).thenReturn(true);

        createService().removeParentFromTeam(teamId, parentId, null);

        verify(extraHatService, never()).revokeHat(any(), any());
    }

    @Test
    void removeParentFromTeam_keepsHatWhenAnotherLinkRemains() {
        UUID teamId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        PlayerProfile child = profile(profileId, "Ann", "Adams", player(UUID.randomUUID(), "ann@test.com"));
        PlayerParent link = parentLink(parentId, child);

        when(playerParentRepository.findLinksForParentOnTeam(parentId, teamId)).thenReturn(List.of(link));
        when(teamRosterRepository.existsByPlayerProfile_IdAndTeam_IdNot(profileId, teamId)).thenReturn(false);
        when(playerParentRepository.findByUser_Id(parentId)).thenReturn(List.of(link));

        createService().removeParentFromTeam(teamId, parentId, null);

        verify(extraHatService, never()).revokeHat(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
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

    @Test
    void addCoachToTeam_grantsCoachHat() {
        UUID teamId = UUID.randomUUID();
        UUID coachId = UUID.randomUUID();
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(teamId, "Arsenal")));
        when(userRepository.findById(coachId)).thenReturn(Optional.of(coach(coachId, "coach@test.com")));
        when(teamMembershipQuery.isCoachOnTeam(coachId, teamId)).thenReturn(false);

        createService().addCoachToTeam(teamId, coachId, null, false);

        verify(teamMembershipQuery).insertCoachTeam(coachId, teamId);
        verify(extraHatService).grantHat(coachId, UserRole.COACH);
    }

    @Test
    void removeCoachFromTeam_revokesHatOnlyWhenNoCoachTeamRemains() {
        UUID teamId = UUID.randomUUID();
        UUID coachId = UUID.randomUUID();
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(teamId, "Arsenal")));
        when(userRepository.findById(coachId)).thenReturn(Optional.of(coach(coachId, "coach@test.com")));
        when(teamMembershipQuery.isCoachOnTeam(coachId, teamId)).thenReturn(true);
        when(teamMembershipQuery.findCoachTeamIds(coachId)).thenReturn(java.util.List.of());

        createService().removeCoachFromTeam(teamId, coachId, null);

        verify(extraHatService).revokeHat(coachId, UserRole.COACH);
    }

    @Test
    void removeCoachFromTeam_keepsHatWhenAnotherTeamRemains() {
        UUID teamId = UUID.randomUUID();
        UUID coachId = UUID.randomUUID();
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team(teamId, "Arsenal")));
        when(userRepository.findById(coachId)).thenReturn(Optional.of(coach(coachId, "coach@test.com")));
        when(teamMembershipQuery.isCoachOnTeam(coachId, teamId)).thenReturn(true);
        when(teamMembershipQuery.findCoachTeamIds(coachId)).thenReturn(java.util.List.of(UUID.randomUUID()));

        createService().removeCoachFromTeam(teamId, coachId, null);

        verify(extraHatService, never()).revokeHat(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
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
