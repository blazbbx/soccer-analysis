package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.TeamInvite;
import com.example.footballanalysis.model.db.user.Coach;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeamInviteService tesztek")
class TeamInviteServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private CoachRepository coachRepository;

    @Mock
    private FanRepository fanRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamInviteRepository teamInviteRepository;

    @Mock
    private TeamService teamService;

    @Mock
    private AuditEventService auditEventService;

    private TeamInviteService teamInviteService;

    @BeforeEach
    void setUp() {
        teamInviteService = new TeamInviteService(teamRepository, coachRepository, fanRepository, playerRepository, userRepository, teamInviteRepository, teamService, auditEventService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.setContextPath("");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void generateInviteToken_usesJwtSubjectWhenAvailable() {
        UUID teamId = UUID.randomUUID();
        UUID coachId = UUID.randomUUID();
        Coach coach = new Coach();
        coach.setId(coachId);
        coach.setEmail("coach@test.com");

        Team team = new Team();
        team.setId(teamId);
        team.setName("Arsenal");

        coach.setTeams(new HashSet<>());
        team.setCoaches(new HashSet<>());
        coach.addTeam(team);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(coachRepository.findById(coachId)).thenReturn(Optional.of(coach));
        when(userRepository.findById(coachId)).thenReturn(Optional.of(coach));
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InviteTokenResponse response = teamInviteService.generateInviteToken(teamId, (User) coach, UserRole.PLAYER);

        assertThat(response.token()).isNotBlank();
        assertThat(response.token()).doesNotContain("/");

        ArgumentCaptor<TeamInvite> inviteCaptor = ArgumentCaptor.forClass(TeamInvite.class);
        verify(teamInviteRepository).save(inviteCaptor.capture());
        assertThat(inviteCaptor.getValue().getMaxUses()).isEqualTo(5);
        assertThat(inviteCaptor.getValue().getExpiresAt()).isNotNull();
        assertThat(inviteCaptor.getValue().getCreatedByUserId()).isEqualTo(coachId);
        assertThat(inviteCaptor.getValue().getCreatedByUserRole()).isEqualTo(UserRole.COACH);
        verify(auditEventService).record(
            org.mockito.ArgumentMatchers.eq("TEAM_INVITE_GENERATED"),
            org.mockito.ArgumentMatchers.eq(coachId),
            org.mockito.ArgumentMatchers.eq(UserRole.COACH.name()),
            org.mockito.ArgumentMatchers.eq("TEAM"),
            org.mockito.ArgumentMatchers.eq(teamId.toString()),
            org.mockito.ArgumentMatchers.contains("requestedRole=PLAYER")
        );
    }

    @Test
    void generateInviteToken_rejectsMissingTeam() {
        UUID teamId = UUID.randomUUID();
        Coach actor = createCoach("coach@test.com");

        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamInviteService.generateInviteToken(teamId, actor, UserRole.PLAYER))
                .isInstanceOf(NotFoundException.class);

        verify(teamInviteRepository, never()).save(any());
    }

    @Test
    void generateInviteToken_rejectsCoachNotAssignedToTeam() {
        UUID teamId = UUID.randomUUID();
        UUID coachId = UUID.randomUUID();
        Coach coach = createCoach(coachId, "coach@test.com");

        Team team = new Team();
        team.setId(teamId);
        team.setName("Arsenal");
        team.setCoaches(new HashSet<>());

        coach.setTeams(new HashSet<>());

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> teamInviteService.generateInviteToken(teamId, coach, UserRole.PLAYER))
                .isInstanceOf(ConflictException.class);

        verify(teamInviteRepository, never()).save(any());
    }

    @Test
    void generateInviteToken_rejectsAdminRoleInvite() {
        UUID teamId = UUID.randomUUID();
        UUID coachId = UUID.randomUUID();
        Coach admin = createAdmin(coachId, "admin@test.com");

        Team team = new Team();
        team.setId(teamId);
        team.setName("Arsenal");
        team.setCoaches(new HashSet<>());

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> teamInviteService.generateInviteToken(teamId, admin, UserRole.ADMIN))
                .isInstanceOf(BadRequestException.class);

        verify(teamInviteRepository, never()).save(any());
    }

    @Test
    void generateInviteToken_allowsPlayerToInviteFan() {
        UUID teamId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Player player = createPlayer(playerId, "player@test.com");

        Team team = new Team();
        team.setId(teamId);
        team.setName("Arsenal");
        team.setPlayers(new HashSet<>());

        player.addTeam(team);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InviteTokenResponse response = teamInviteService.generateInviteToken(teamId, player, UserRole.FAN);

        assertThat(response.token()).isNotBlank();

        ArgumentCaptor<TeamInvite> inviteCaptor = ArgumentCaptor.forClass(TeamInvite.class);
        verify(teamInviteRepository).save(inviteCaptor.capture());
        assertThat(inviteCaptor.getValue().getInvitedRole()).isEqualTo(UserRole.FAN);
        assertThat(inviteCaptor.getValue().getCreatedByUserId()).isEqualTo(playerId);
        assertThat(inviteCaptor.getValue().getCreatedByUserRole()).isEqualTo(UserRole.PLAYER);
        verify(auditEventService).record(
            org.mockito.ArgumentMatchers.eq("TEAM_INVITE_GENERATED"),
            org.mockito.ArgumentMatchers.eq(playerId),
            org.mockito.ArgumentMatchers.eq(UserRole.PLAYER.name()),
            org.mockito.ArgumentMatchers.eq("TEAM"),
            org.mockito.ArgumentMatchers.eq(teamId.toString()),
            org.mockito.ArgumentMatchers.contains("requestedRole=FAN")
        );
    }

    @Test
    void generateInviteToken_rejectsMissingRole() {
        UUID teamId = UUID.randomUUID();
        Player actor = createPlayer("player@test.com");

        assertThatThrownBy(() -> teamInviteService.generateInviteToken(teamId, actor, null))
            .isInstanceOf(BadRequestException.class);

        verify(teamInviteRepository, never()).save(any());
    }

    @Test
    void generateInviteToken_rejectsPlayerInvitingNonFanRole() {
        UUID teamId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Player player = createPlayer(playerId, "player@test.com");

        Team team = new Team();
        team.setId(teamId);
        team.setName("Arsenal");
        team.setPlayers(new HashSet<>());

        player.addTeam(team);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> teamInviteService.generateInviteToken(teamId, player, UserRole.PLAYER))
                .isInstanceOf(BadRequestException.class);

        verify(teamInviteRepository, never()).save(any());
    }

    @Test
    void getInvite_rejectsMissingToken() {
        when(teamInviteRepository.findByToken("missing-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamInviteService.getInvite("missing-token"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getInvite_rejectsExpiredInvite() {
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setName("Arsenal");

        TeamInvite invite = new TeamInvite();
        invite.setTeam(team);
        invite.setInvitedRole(UserRole.PLAYER);
        invite.setToken("invite-token");
        invite.setMaxUses(30);
        invite.setUsedCount(0);
        invite.setExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));

        when(teamInviteRepository.findByToken("invite-token")).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> teamInviteService.getInvite("invite-token"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void acceptInvite_incrementsUsageAndDelegatesToTeamService() {
        UUID teamId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Player player = createPlayer(playerId, "player@test.com");

        Team team = new Team();
        team.setId(teamId);
        team.setName("Arsenal");

        team.setCoaches(new HashSet<>());

        TeamInvite invite = new TeamInvite();
        invite.setTeam(team);
        invite.setInvitedRole(UserRole.PLAYER);
        invite.setToken("invite-token");
        invite.setMaxUses(30);
        invite.setUsedCount(0);

        when(teamInviteRepository.findByTokenForUpdate("invite-token")).thenReturn(Optional.of(invite));
        when(playerRepository.findByEmail("player@test.com")).thenReturn(Optional.of(playerWithId(playerId)));
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TeamInviteResponse response = teamInviteService.acceptInvite(
                "invite-token",
                player
        );

        verify(teamService).addPlayerToTeam(teamId, playerId);
        assertThat(response.usedCount()).isEqualTo(1);
        assertThat(response.remainingUses()).isEqualTo(29);
        verify(auditEventService).record(
            org.mockito.ArgumentMatchers.eq("TEAM_INVITE_ACCEPTED"),
            org.mockito.ArgumentMatchers.eq(playerId),
            org.mockito.ArgumentMatchers.eq(UserRole.PLAYER.name()),
            org.mockito.ArgumentMatchers.eq("TEAM"),
            org.mockito.ArgumentMatchers.eq(teamId.toString()),
            org.mockito.ArgumentMatchers.contains("flow=direct_accept")
        );
    }

    @Test
    void acceptInvite_rejectsMissingInvite() {
        when(teamInviteRepository.findByTokenForUpdate("missing-token")).thenReturn(Optional.empty());

        Player player = createPlayer("player@test.com");

        assertThatThrownBy(() -> teamInviteService.acceptInvite("missing-token", player))
                .isInstanceOf(NotFoundException.class);

        verify(teamService, never()).addPlayerToTeam(any(), any());
    }

    @Test
    void acceptInvite_rejectsExpiredInvite() {
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setName("Arsenal");

        TeamInvite invite = new TeamInvite();
        invite.setTeam(team);
        invite.setInvitedRole(UserRole.PLAYER);
        invite.setToken("invite-token");
        invite.setMaxUses(30);
        invite.setUsedCount(0);
        invite.setExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));

        Player player = createPlayer("player@test.com");

        when(teamInviteRepository.findByTokenForUpdate("invite-token")).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> teamInviteService.acceptInvite("invite-token", player))
                .isInstanceOf(ConflictException.class);

        verify(teamService, never()).addPlayerToTeam(any(), any());
    }

    @Test
    void acceptInvite_rejectsMissingJwt() {
        assertThatThrownBy(() -> teamInviteService.acceptInvite("invite-token", null))
                .isInstanceOf(UnauthorizedException.class);

        verify(teamInviteRepository, never()).findByTokenForUpdate(any());
        verify(teamService, never()).addPlayerToTeam(any(), any());
    }

    @Test
    void acceptInvite_rejectsWhenLinkIsExhausted() {
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setName("Arsenal");

        TeamInvite invite = new TeamInvite();
        invite.setTeam(team);
        invite.setInvitedRole(UserRole.PLAYER);
        invite.setToken("invite-token");
        invite.setMaxUses(30);
        invite.setUsedCount(30);

        when(teamInviteRepository.findByTokenForUpdate("invite-token")).thenReturn(Optional.of(invite));

        Player player = createPlayer("player@test.com");

        assertThatThrownBy(() -> teamInviteService.acceptInvite(
                "invite-token",
                player
        )).isInstanceOf(ConflictException.class);

        verify(teamService, never()).addPlayerToTeam(any(UUID.class), any(UUID.class));
    }

    private Coach createCoach(String email) {
        Coach coach = new Coach();
        coach.setId(UUID.randomUUID());
        coach.setEmail(email);
        coach.setRole(UserRole.COACH);
        return coach;
    }

    private Coach createCoach(UUID id, String email) {
        Coach coach = new Coach();
        coach.setId(id);
        coach.setEmail(email);
        coach.setRole(UserRole.COACH);
        return coach;
    }

    private Coach createAdmin(UUID id, String email) {
        Coach admin = new Coach();
        admin.setId(id);
        admin.setEmail(email);
        admin.setRole(UserRole.ADMIN);
        return admin;
    }

    private Player createPlayer(String email) {
        Player player = new Player();
        player.setId(UUID.randomUUID());
        player.setEmail(email);
        player.setRole(UserRole.PLAYER);
        return player;
    }

    private Player createPlayer(UUID id, String email) {
        Player player = new Player();
        player.setId(id);
        player.setEmail(email);
        player.setRole(UserRole.PLAYER);
        return player;
    }

    private Player playerWithId(UUID playerId) {
        Player player = new Player();
        player.setId(playerId);
        return player;
    }
}