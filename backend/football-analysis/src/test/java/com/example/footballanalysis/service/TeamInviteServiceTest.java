package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.model.db.InviteKind;
import com.example.footballanalysis.model.requests.AcceptInviteRequest;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.PlayerParent;
import com.example.footballanalysis.model.db.PlayerParentId;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.TeamInvite;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.user.ClubAdmin;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Fan;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.responses.InviteTokenResponse;
import com.example.footballanalysis.model.responses.TeamInviteResponse;
import com.example.footballanalysis.service.PlayerParentService;
import com.example.footballanalysis.repository.PlayerParentRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.TeamInviteRepository;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import com.example.footballanalysis.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeamInviteService tesztek")
class TeamInviteServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamInviteRepository teamInviteRepository;

    @Mock
    private TeamService teamService;

    @Mock
    private AuditEventService auditEventService;

    @Mock
    private PlayerProfileRepository playerProfileRepository;

    @Mock
    private PlayerParentRepository playerParentRepository;

    @Mock
    private PlayerParentService playerParentService;

    @Mock
    private ParentInviteService parentInviteService;

    @Mock
    private ClubAdminInviteService clubAdminInviteService;

    @Mock
    private UserAccessService userAccessService;

    @Mock
    private TeamMembershipQuery teamMembershipQuery;

    @Mock
    private ExtraHatService extraHatService;

    private TeamInviteService teamInviteService;

    @BeforeEach
    void setUp() {
        teamInviteService = new TeamInviteService(teamRepository, teamInviteRepository, teamService, auditEventService, playerProfileRepository, playerParentRepository, playerParentService, parentInviteService, clubAdminInviteService, userAccessService, teamMembershipQuery, extraHatService);
        lenient().when(playerProfileRepository.findByLinkedUser_Id(any())).thenReturn(Optional.empty());
        lenient().when(playerProfileRepository.existsByLinkedUser_Id(any())).thenReturn(false);

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
        SecurityContextHolder.clearContext();
    }

    @Test
    void generateInviteToken_usesJwtSubjectWhenAvailable() {
        UUID teamId = UUID.randomUUID();
        UUID coachId = UUID.randomUUID();
        Coach coach = new Coach();
        coach.setId(coachId);
        coach.setEmail("coach@test.com");
        coach.setRole(UserRole.COACH);

        Team team = new Team();
        team.setId(teamId);
        team.setName("Arsenal");

        coach.setTeams(new HashSet<>());
        team.setCoaches(new HashSet<>());
        coach.addTeam(team);
        team.getCoaches().add(coach);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMembershipQuery.isCoachOnTeam(coachId, teamId)).thenReturn(true);
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InviteTokenResponse response = teamInviteService.generateInviteToken(teamId, (User) coach, UserRole.PLAYER, UserRole.COACH);

        assertThat(response.token()).isNotBlank();
        assertThat(response.token()).doesNotContain("/");

        ArgumentCaptor<TeamInvite> inviteCaptor = ArgumentCaptor.forClass(TeamInvite.class);
        verify(teamInviteRepository).save(inviteCaptor.capture());
        assertThat(inviteCaptor.getValue().getMaxUses()).isEqualTo(20);
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
    void generateInviteToken_allowsClubAdminOfThatClub() {
        UUID teamId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        ClubAdmin clubAdmin = new ClubAdmin();
        clubAdmin.setId(adminId);
        clubAdmin.setRole(UserRole.CLUB_ADMIN);
        Club club = new Club();
        club.setId(clubId);
        Team team = new Team();
        team.setId(teamId);
        team.setClub(club);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(userAccessService.isClubAdmin(clubAdmin, clubId)).thenReturn(true);
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InviteTokenResponse playerInvite = teamInviteService.generateInviteToken(teamId, clubAdmin, UserRole.PLAYER, UserRole.CLUB_ADMIN);
        InviteTokenResponse parentInvite = teamInviteService.generateInviteToken(teamId, clubAdmin, UserRole.PARENT, UserRole.CLUB_ADMIN);

        assertThat(playerInvite.token()).isNotBlank();
        assertThat(parentInvite.token()).isNotBlank();
        ArgumentCaptor<TeamInvite> inviteCaptor = ArgumentCaptor.forClass(TeamInvite.class);
        verify(teamInviteRepository, org.mockito.Mockito.times(2)).save(inviteCaptor.capture());
        assertThat(inviteCaptor.getAllValues()).extracting(TeamInvite::getCreatedByUserRole).containsOnly(UserRole.CLUB_ADMIN);
        assertThat(inviteCaptor.getAllValues()).extracting(TeamInvite::getInvitedRole).containsExactly(UserRole.PLAYER, UserRole.PARENT);
    }

    @Test
    void generateInviteToken_rejectsClubAdminOfAnotherClub() {
        UUID teamId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        ClubAdmin clubAdmin = new ClubAdmin();
        clubAdmin.setId(UUID.randomUUID());
        clubAdmin.setRole(UserRole.CLUB_ADMIN);
        Club club = new Club();
        club.setId(clubId);
        Team team = new Team();
        team.setId(teamId);
        team.setClub(club);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(userAccessService.isClubAdmin(clubAdmin, clubId)).thenReturn(false);

        assertThatThrownBy(() -> teamInviteService.generateInviteToken(teamId, clubAdmin, UserRole.COACH, UserRole.CLUB_ADMIN))
                .isInstanceOf(ForbiddenException.class);
        verify(teamInviteRepository, never()).save(any());
    }

    @Test
    void generateInviteToken_rejectsMissingTeam() {
        UUID teamId = UUID.randomUUID();
        Coach actor = createCoach("coach@test.com");

        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamInviteService.generateInviteToken(teamId, actor, UserRole.PLAYER, UserRole.COACH))
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

        assertThatThrownBy(() -> teamInviteService.generateInviteToken(teamId, coach, UserRole.PLAYER, UserRole.COACH))
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

        assertThatThrownBy(() -> teamInviteService.generateInviteToken(teamId, admin, UserRole.ADMIN, UserRole.ADMIN))
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
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerProfileRepository.existsByLinkedUser_IdAndTeam_Id(playerId, teamId)).thenReturn(true);
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InviteTokenResponse response = teamInviteService.generateInviteToken(teamId, player, UserRole.FAN, UserRole.PLAYER);

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
    void validateAcceptance_rejectsPlayerUnder16() {
        TeamInvite invite = new TeamInvite();
        invite.setInviteKind(InviteKind.PLAYER_SELF);
        invite.setInvitedRole(UserRole.PLAYER);

        assertThatThrownBy(() -> teamInviteService.validateAcceptance(
                invite,
                new AcceptInviteRequest(null, null, null, java.time.LocalDate.now().minusYears(15), null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void acceptInvite_existingPlayerSkipsDateOfBirth() {
        UUID teamId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Player player = createPlayer(playerId, "player@test.com");

        Team team = new Team();
        team.setId(teamId);
        team.setName("Arsenal");

        TeamInvite invite = new TeamInvite();
        invite.setId(UUID.randomUUID());
        invite.setCreatedByUserId(UUID.randomUUID());
        invite.setTeam(team);
        invite.setInviteKind(InviteKind.PLAYER_SELF);
        invite.setInvitedRole(UserRole.PLAYER);
        invite.setToken("invite-token");
        invite.setMaxUses(20);
        invite.setUsedCount(0);
        invite.setExpiresAt(LocalDateTime.now().plusHours(2));

        PlayerProfile existing = new PlayerProfile();
        existing.setId(UUID.randomUUID());
        existing.setDateOfBirth(LocalDate.now().minusYears(12));
        when(playerProfileRepository.existsByLinkedUser_Id(playerId)).thenReturn(true);
        when(teamInviteRepository.findByTokenForUpdate("invite-token")).thenReturn(Optional.of(invite));
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        teamInviteService.acceptInvite("invite-token", player, AcceptInviteRequest.empty());

        verify(teamService).addPlayerToTeam(teamId, playerId, player, false);
        verify(teamService, never()).assignLinkedDateOfBirth(any(), any());
        // The existing-player branch consumes the invite like every other kind.
        assertThat(invite.getUsedCount()).isEqualTo(1);
        verify(teamInviteRepository).save(invite);
    }

    @Test
    void acceptInvite_existingPlayerCannotUseAnExpiredInvite() {
        UUID teamId = UUID.randomUUID();
        Team team = new Team();
        team.setId(teamId);
        Player player = new Player();
        player.setId(UUID.randomUUID());
        player.setRole(UserRole.PLAYER);

        TeamInvite invite = new TeamInvite();
        invite.setId(UUID.randomUUID());
        invite.setTeam(team);
        invite.setInviteKind(InviteKind.PLAYER_SELF);
        invite.setInvitedRole(UserRole.PLAYER);
        invite.setToken("expired-token");
        invite.setMaxUses(20);
        invite.setUsedCount(0);
        invite.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        lenient().when(playerProfileRepository.existsByLinkedUser_Id(player.getId())).thenReturn(true);
        when(teamInviteRepository.findByTokenForUpdate("expired-token")).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> teamInviteService.acceptInvite("expired-token", player, AcceptInviteRequest.empty()))
                .isInstanceOf(ConflictException.class);
        verify(teamService, never()).addPlayerToTeam(any(), any(), any(), anyBoolean());
        assertThat(invite.getUsedCount()).isZero();
    }

    @Test
    void validateAcceptance_openParentRequiresChildDetails() {
        TeamInvite invite = new TeamInvite();
        invite.setInviteKind(InviteKind.PARENT);
        invite.setInvitedRole(UserRole.PARENT);

        assertThatThrownBy(() -> teamInviteService.validateAcceptance(invite, AcceptInviteRequest.empty()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void generateInviteToken_rejectsMissingRole() {
        UUID teamId = UUID.randomUUID();
        Player actor = createPlayer("player@test.com");

        assertThatThrownBy(() -> teamInviteService.generateInviteToken(teamId, actor, null, UserRole.PLAYER))
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
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerProfileRepository.existsByLinkedUser_IdAndTeam_Id(playerId, teamId)).thenReturn(true);

        assertThatThrownBy(() -> teamInviteService.generateInviteToken(teamId, player, UserRole.PLAYER, UserRole.PLAYER))
                .isInstanceOf(BadRequestException.class);

        verify(teamInviteRepository, never()).save(any());
    }

    @Test
    void getInvite_rejectsMissingToken() {
        when(teamInviteRepository.findByToken("missing-token")).thenReturn(Optional.empty());
        when(clubAdminInviteService.getInvite("missing-token")).thenThrow(new NotFoundException("error.team.invite.not_found", new Object[0], "Invite not found."));

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
    void getInvite_omitsRawTokenFromResponse() {
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setName("Arsenal");

        TeamInvite invite = new TeamInvite();
        invite.setId(UUID.randomUUID());
        invite.setTeam(team);
        invite.setInvitedRole(UserRole.PLAYER);
        invite.setToken("secret-invite-token");
        invite.setMaxUses(5);
        invite.setUsedCount(1);
        invite.setExpiresAt(java.time.LocalDateTime.now().plusHours(2));

        when(teamInviteRepository.findByToken("secret-invite-token")).thenReturn(Optional.of(invite));

        TeamInviteResponse response = teamInviteService.getInvite("secret-invite-token");

        assertThat(response.token()).isNull();
        assertThat(response.teamName()).isEqualTo("Arsenal");
        assertThat(response.inviteLink()).contains("secret-invite-token");
        assertThat(response.remainingUses()).isEqualTo(4);
    }

    @Test
    void acceptInvite_incrementsUsageAndDelegatesToTeamService() {
        UUID teamId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();
        Player player = createPlayer(playerId, "player@test.com");

        Team team = new Team();
        team.setId(teamId);
        team.setName("Arsenal");

        team.setCoaches(new HashSet<>());

        TeamInvite invite = new TeamInvite();
        invite.setId(inviteId);
        invite.setCreatedByUserId(createdBy);
        invite.setTeam(team);
        invite.setInvitedRole(UserRole.PLAYER);
        invite.setToken("invite-token");
        invite.setMaxUses(30);
        invite.setUsedCount(0);

        when(teamInviteRepository.findByTokenForUpdate("invite-token")).thenReturn(Optional.of(invite));
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TeamInviteResponse response = teamInviteService.acceptInvite(
                "invite-token",
                player,
                new AcceptInviteRequest(null, null, null, java.time.LocalDate.now().minusYears(20), null)
        );

        verify(teamService).addPlayerToTeam(teamId, playerId, player, false);
        assertThat(response.usedCount()).isEqualTo(1);
        assertThat(response.remainingUses()).isEqualTo(29);
        verify(auditEventService).record(
            org.mockito.ArgumentMatchers.eq("TEAM_INVITE_ACCEPTED"),
            org.mockito.ArgumentMatchers.eq(playerId),
            org.mockito.ArgumentMatchers.eq(UserRole.PLAYER.name()),
            org.mockito.ArgumentMatchers.eq("TEAM"),
            org.mockito.ArgumentMatchers.eq(teamId.toString()),
            org.mockito.ArgumentMatchers.contains("flow=direct_accept, inviteId=" + inviteId + ", createdBy=" + createdBy)
        );
    }

    @Test
    void acceptInvite_rejectsMissingInvite() {
        when(teamInviteRepository.findByTokenForUpdate("missing-token")).thenReturn(Optional.empty());
        when(clubAdminInviteService.accept(org.mockito.ArgumentMatchers.eq("missing-token"), any())).thenThrow(new NotFoundException("error.team.invite.not_found", new Object[0], "Invite not found."));

        Player player = createPlayer("player@test.com");

        assertThatThrownBy(() -> teamInviteService.acceptInvite("missing-token", player))
                .isInstanceOf(NotFoundException.class);

        verify(teamService, never()).addPlayerToTeam(any(), any(), any(), anyBoolean());
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

        verify(teamService, never()).addPlayerToTeam(any(), any(), any(), anyBoolean());
    }

    @Test
    void acceptInvite_rejectsMissingJwt() {
        assertThatThrownBy(() -> teamInviteService.acceptInvite("invite-token", null))
                .isInstanceOf(UnauthorizedException.class);

        verify(teamInviteRepository, never()).findByTokenForUpdate(any());
        verify(teamService, never()).addPlayerToTeam(any(), any(), any(), anyBoolean());
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

        verify(teamService, never()).addPlayerToTeam(any(), any(), any(), anyBoolean());
    }

    @Test
    void acceptInvite_parentCanAddSecondChildOnSameInvite() {
        UUID teamId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Parent parent = new Parent();
        parent.setId(parentId);
        parent.setEmail("parent@test.test");
        parent.setRole(UserRole.PARENT);

        Team team = new Team();
        team.setId(teamId);
        team.setName("Arsenal");

        TeamInvite invite = new TeamInvite();
        invite.setId(UUID.randomUUID());
        invite.setCreatedByUserId(UUID.randomUUID());
        invite.setTeam(team);
        invite.setInvitedRole(UserRole.PARENT);
        invite.setInviteKind(InviteKind.PARENT);
        invite.setToken("invite-token");
        invite.setMaxUses(20);
        invite.setUsedCount(1);
        invite.setExpiresAt(LocalDateTime.now().plusHours(2));

        PlayerProfile created = new PlayerProfile();
        created.setId(UUID.randomUUID());
        when(teamInviteRepository.findByTokenForUpdate("invite-token")).thenReturn(Optional.of(invite));
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(teamService.addRosterProfile(eq(teamId), eq("Bela"), eq("Teszt"), any(LocalDate.class)))
                .thenReturn(created);

        TeamInviteResponse response = teamInviteService.acceptInvite(
                "invite-token",
                parent,
                new AcceptInviteRequest("Bela", "Teszt", LocalDate.now().minusYears(8), null, null));

        verify(playerParentService).link(parent, created);
        assertThat(response.usedCount()).isEqualTo(2);
        assertThat(response.remainingUses()).isEqualTo(18);
    }

    @Test
    void acceptInvite_deferredRegistrationDoesNotConsumeASecondUse() {
        UUID teamId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Parent parent = new Parent();
        parent.setId(parentId);
        parent.setEmail("parent@test.test");
        parent.setRole(UserRole.PARENT);

        Team team = new Team();
        team.setId(teamId);
        team.setName("Arsenal");

        TeamInvite invite = new TeamInvite();
        invite.setId(UUID.randomUUID());
        invite.setCreatedByUserId(UUID.randomUUID());
        invite.setTeam(team);
        invite.setInvitedRole(UserRole.PARENT);
        invite.setInviteKind(InviteKind.PARENT);
        invite.setToken("invite-token");
        invite.setMaxUses(1);
        invite.setUsedCount(1);
        invite.setExpiresAt(LocalDateTime.now().plusHours(2));
        invite.reserveRegistration(parentId);

        PlayerProfile created = new PlayerProfile();
        created.setId(UUID.randomUUID());
        when(teamInviteRepository.findByTokenForUpdate("invite-token")).thenReturn(Optional.of(invite));
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(teamService.addRosterProfile(eq(teamId), eq("Bela"), eq("Teszt"), any(LocalDate.class)))
                .thenReturn(created);

        TeamInviteResponse response = teamInviteService.acceptInvite(
                "invite-token",
                parent,
                new AcceptInviteRequest("Bela", "Teszt", LocalDate.now().minusYears(8), null, null));

        verify(playerParentService).link(parent, created);
        assertThat(response.usedCount()).isEqualTo(1);
        assertThat(response.remainingUses()).isZero();
        assertThat(invite.claimPendingRegistration(parentId)).isFalse();
    }

    @Test
    void acceptInvite_finishesReservedRegistrationAfterExpiry() {
        UUID teamId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Parent parent = new Parent();
        parent.setId(parentId);
        parent.setEmail("parent@test.test");
        parent.setRole(UserRole.COACH);

        Team team = new Team();
        team.setId(teamId);
        team.setName("Arsenal");

        TeamInvite invite = new TeamInvite();
        invite.setId(UUID.randomUUID());
        invite.setCreatedByUserId(UUID.randomUUID());
        invite.setTeam(team);
        invite.setInvitedRole(UserRole.PARENT);
        invite.setInviteKind(InviteKind.PARENT);
        invite.setToken("invite-token");
        invite.setMaxUses(1);
        invite.setUsedCount(1);
        invite.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        invite.reserveRegistration(parentId);

        PlayerProfile created = new PlayerProfile();
        created.setId(UUID.randomUUID());
        when(teamInviteRepository.findByTokenForUpdate("invite-token")).thenReturn(Optional.of(invite));
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(teamService.addRosterProfile(eq(teamId), eq("Bela"), eq("Teszt"), any(LocalDate.class)))
                .thenReturn(created);

        TeamInviteResponse response = teamInviteService.acceptInvite(
                "invite-token",
                parent,
                new AcceptInviteRequest("Bela", "Teszt", LocalDate.now().minusYears(8), null, null));

        verify(playerParentService).link(parent, created);
        assertThat(response.usedCount()).isEqualTo(1);
        verify(auditEventService).record(
                eq("TEAM_INVITE_ACCEPTED"),
                eq(parentId),
                eq(UserRole.COACH.name()),
                eq("TEAM"),
                eq(teamId.toString()),
                org.mockito.ArgumentMatchers.contains("flow=direct_accept"));
    }

    @Test
    void acceptInvite_rejectsAnotherParentWhenDeferredRegistrationFilledTheInvite() {
        UUID parentId = UUID.randomUUID();
        Parent parent = new Parent();
        parent.setId(parentId);
        parent.setRole(UserRole.PARENT);

        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setName("Arsenal");

        TeamInvite invite = new TeamInvite();
        invite.setId(UUID.randomUUID());
        invite.setTeam(team);
        invite.setInvitedRole(UserRole.PARENT);
        invite.setInviteKind(InviteKind.PARENT);
        invite.setToken("invite-token");
        invite.setMaxUses(1);
        invite.setUsedCount(1);
        invite.setExpiresAt(LocalDateTime.now().plusHours(2));
        invite.reserveRegistration(UUID.randomUUID());

        when(teamInviteRepository.findByTokenForUpdate("invite-token")).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> teamInviteService.acceptInvite(
                "invite-token",
                parent,
                new AcceptInviteRequest("Bela", "Teszt", LocalDate.now().minusYears(8), null, null)))
                .isInstanceOf(ConflictException.class);

        verify(teamService, never()).addRosterProfile(any(), any(), any(), any());
        assertThat(invite.getUsedCount()).isEqualTo(1);
    }

    @Test
    void createChildLoginInvite_parentGetsToken() {
        UUID profileId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Parent parent = new Parent();
        parent.setId(parentId);
        parent.setRole(UserRole.PARENT);
        PlayerProfile profile = childProfile(profileId);
        Team rosterTeam = new Team();
        rosterTeam.setId(UUID.randomUUID());
        rosterTeam.setName("U11");
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(playerProfileRepository.findTeamsByProfileId(profileId)).thenReturn(java.util.List.of(rosterTeam));
        when(playerParentRepository.existsById(any())).thenReturn(true);
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InviteTokenResponse response = teamInviteService.createChildLoginInvite(profileId, parent);

        assertThat(response.token()).isNotBlank();
        ArgumentCaptor<TeamInvite> saved = ArgumentCaptor.forClass(TeamInvite.class);
        verify(teamInviteRepository).save(saved.capture());
        assertThat(saved.getValue().getInviteKind()).isEqualTo(InviteKind.CHILD_LOGIN);
        assertThat(saved.getValue().getPlayerProfile()).isEqualTo(profile);
        assertThat(saved.getValue().getMaxUses()).isEqualTo(1);
    }

    @Test
    void createChildLoginInvite_clubAdminOfProfileGetsToken() {
        UUID profileId = UUID.randomUUID();
        ClubAdmin clubAdmin = new ClubAdmin();
        clubAdmin.setId(UUID.randomUUID());
        clubAdmin.setRole(UserRole.CLUB_ADMIN);
        PlayerProfile profile = childProfile(profileId);
        Team rosterTeam = new Team();
        rosterTeam.setId(UUID.randomUUID());
        rosterTeam.setName("U11");
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(playerProfileRepository.findTeamsByProfileId(profileId)).thenReturn(java.util.List.of(rosterTeam));
        when(playerParentRepository.existsById(any())).thenReturn(false);
        when(userAccessService.isClubAdminOfProfile(clubAdmin, profileId)).thenReturn(true);
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InviteTokenResponse response = teamInviteService.createChildLoginInvite(profileId, clubAdmin);

        assertThat(response.token()).isNotBlank();
    }

    @Test
    void createChildLoginInvite_bindsTheTeamTheCallerAdministers() {
        UUID profileId = UUID.randomUUID();
        ClubAdmin clubAdmin = new ClubAdmin();
        clubAdmin.setId(UUID.randomUUID());
        clubAdmin.setRole(UserRole.CLUB_ADMIN);
        PlayerProfile profile = childProfile(profileId);
        Club alphaClub = new Club();
        alphaClub.setId(UUID.randomUUID());
        Club betaClub = new Club();
        betaClub.setId(UUID.randomUUID());
        Team alpha = new Team();
        alpha.setId(UUID.randomUUID());
        alpha.setName("Alpha");
        alpha.setClub(alphaClub);
        Team beta = new Team();
        beta.setId(UUID.randomUUID());
        beta.setName("Beta");
        beta.setClub(betaClub);
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(playerProfileRepository.findTeamsByProfileId(profileId)).thenReturn(List.of(alpha, beta));
        when(playerParentRepository.existsById(any())).thenReturn(false);
        when(userAccessService.isClubAdminOfProfile(clubAdmin, profileId)).thenReturn(true);
        when(userAccessService.isClubAdmin(clubAdmin, alphaClub.getId())).thenReturn(false);
        when(userAccessService.isClubAdmin(clubAdmin, betaClub.getId())).thenReturn(true);
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        teamInviteService.createChildLoginInvite(profileId, clubAdmin);

        ArgumentCaptor<TeamInvite> saved = ArgumentCaptor.forClass(TeamInvite.class);
        verify(teamInviteRepository).save(saved.capture());
        assertThat(saved.getValue().getTeam()).isEqualTo(beta);
    }

    @Test
    void requireEligibleChildLogin_allowsTeamlessProfileWhenAParentExists() {
        UUID profileId = UUID.randomUUID();
        Parent parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setRole(UserRole.PARENT);
        PlayerProfile profile = childProfile(profileId);
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(playerProfileRepository.findTeamsByProfileId(profileId)).thenReturn(List.of());
        when(playerParentRepository.existsById(any())).thenReturn(true);
        when(playerParentRepository.existsByPlayerProfile_Id(profileId)).thenReturn(true);

        assertThat(teamInviteService.requireEligibleChildLogin(profileId, parent)).isEqualTo(profile);
    }

    @Test
    void createChildLoginInvite_rejectsTeamlessProfile() {
        UUID profileId = UUID.randomUUID();
        Parent parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setRole(UserRole.PARENT);
        PlayerProfile profile = childProfile(profileId);
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(playerProfileRepository.findTeamsByProfileId(profileId)).thenReturn(List.of());
        when(playerParentRepository.existsById(any())).thenReturn(true);
        when(playerParentRepository.existsByPlayerProfile_Id(profileId)).thenReturn(true);

        assertThatThrownBy(() -> teamInviteService.createChildLoginInvite(profileId, parent))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getMessageCode())
                .isEqualTo("error.team.invite.profile_not_on_team");
        verify(teamInviteRepository, never()).save(any());
    }

    @Test
    void createChildLoginInvite_rejectsNonParent() {
        UUID profileId = UUID.randomUUID();
        Coach coach = createCoach("coach@test.com");
        PlayerProfile profile = childProfile(profileId);
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(playerParentRepository.existsById(any())).thenReturn(false);

        assertThatThrownBy(() -> teamInviteService.createChildLoginInvite(profileId, coach))
                .isInstanceOf(ForbiddenException.class);
        verify(teamInviteRepository, never()).save(any());
    }

    @Test
    void acceptInvite_existingChildInSameClubJoinsThatRoster() {
        UUID teamId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Club club = new Club();
        club.setId(UUID.randomUUID());
        Parent parent = new Parent();
        parent.setId(parentId);
        parent.setRole(UserRole.PARENT);
        Team team = new Team();
        team.setId(teamId);
        team.setName("U13");
        team.setClub(club);
        PlayerProfile profile = childProfile(profileId);
        TeamInvite invite = parentInvite(team);

        when(teamInviteRepository.findByTokenForUpdate("invite-token")).thenReturn(Optional.of(invite));
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(playerParentRepository.existsById(new PlayerParentId(parentId, profileId))).thenReturn(true);

        teamInviteService.acceptInvite(
                "invite-token",
                parent,
                new AcceptInviteRequest(null, null, null, null, profileId));

        verify(teamService).addProfileToTeam(profile, team);
        verify(teamService, never()).addRosterProfile(any(), any(), any(), any());
        verify(extraHatService, never()).grantHat(any(), any());
    }

    @Test
    void acceptInvite_existingLinkedChildGrantsPlayerHatAfterRosterSave() {
        UUID teamId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID linkedId = UUID.randomUUID();
        Parent parent = new Parent();
        parent.setId(parentId);
        parent.setRole(UserRole.PARENT);
        Player linked = new Player();
        linked.setId(linkedId);
        linked.setRole(UserRole.PLAYER);
        Team team = new Team();
        team.setId(teamId);
        team.setName("U13");
        PlayerProfile profile = childProfile(profileId);
        profile.setLinkedUser(linked);
        TeamInvite invite = parentInvite(team);

        when(teamInviteRepository.findByTokenForUpdate("invite-token")).thenReturn(Optional.of(invite));
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(playerParentRepository.existsById(new PlayerParentId(parentId, profileId))).thenReturn(true);

        teamInviteService.acceptInvite(
                "invite-token",
                parent,
                new AcceptInviteRequest(null, null, null, null, profileId));

        InOrder order = inOrder(teamService, extraHatService);
        order.verify(teamService).addProfileToTeam(profile, team);
        order.verify(extraHatService).grantHat(linkedId, UserRole.PLAYER);
    }

    @Test
    void acceptInvite_existingChildInAnotherClubJoinsThatRoster() {
        UUID teamId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Club fradi = new Club();
        fradi.setId(UUID.randomUUID());
        Parent parent = new Parent();
        parent.setId(parentId);
        parent.setRole(UserRole.PARENT);
        Team team = new Team();
        team.setId(teamId);
        team.setName("U11");
        team.setClub(fradi);
        PlayerProfile profile = childProfile(profileId);
        TeamInvite invite = parentInvite(team);

        when(teamInviteRepository.findByTokenForUpdate("invite-token")).thenReturn(Optional.of(invite));
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(playerParentRepository.existsById(new PlayerParentId(parentId, profileId))).thenReturn(true);

        teamInviteService.acceptInvite(
                "invite-token",
                parent,
                new AcceptInviteRequest(null, null, null, null, profileId));

        verify(teamService).addProfileToTeam(profile, team);
        verify(teamService, never()).addRosterProfile(any(), any(), any(), any());
    }

    @Test
    void acceptInvite_existingChildRejectsLinkedUserWhoIsCoachOrFan() {
        UUID teamId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Parent parent = new Parent();
        parent.setId(parentId);
        parent.setRole(UserRole.PARENT);
        Team team = new Team();
        team.setId(teamId);
        team.setName("U13");
        Player linked = new Player();
        linked.setId(UUID.randomUUID());
        linked.setRole(UserRole.PLAYER);
        PlayerProfile profile = childProfile(profileId);
        profile.setLinkedUser(linked);
        TeamInvite invite = parentInvite(team);

        when(teamInviteRepository.findByTokenForUpdate("invite-token")).thenReturn(Optional.of(invite));
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(playerParentRepository.existsById(new PlayerParentId(parentId, profileId))).thenReturn(true);
        doThrow(new ConflictException(
                "error.team.role_conflict",
                new Object[0],
                "This account already has another role on this team."))
                .when(extraHatService)
                .requireTeamRoleFree(linked.getId(), teamId, UserRole.PLAYER);

        assertThatThrownBy(() -> teamInviteService.acceptInvite(
                "invite-token",
                parent,
                new AcceptInviteRequest(null, null, null, null, profileId)))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.team.role_conflict");

        verify(teamService, never()).addProfileToTeam(any(), any());
    }

    @Test
    void acceptInvite_existingChildRejectsWhenAnotherParentIsAlreadyAPlayer() {
        UUID teamId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Parent parent = new Parent();
        parent.setId(parentId);
        parent.setRole(UserRole.PARENT);
        Parent otherParent = new Parent();
        otherParent.setId(UUID.randomUUID());
        otherParent.setRole(UserRole.PARENT);
        Team team = new Team();
        team.setId(teamId);
        team.setName("U13");
        PlayerProfile profile = childProfile(profileId);
        TeamInvite invite = parentInvite(team);
        PlayerParent acceptingLink = new PlayerParent();
        acceptingLink.setUser(parent);
        PlayerParent otherLink = new PlayerParent();
        otherLink.setUser(otherParent);

        when(teamInviteRepository.findByTokenForUpdate("invite-token")).thenReturn(Optional.of(invite));
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(playerParentRepository.existsById(new PlayerParentId(parentId, profileId))).thenReturn(true);
        when(playerParentRepository.findByPlayerProfile_Id(profileId)).thenReturn(List.of(acceptingLink, otherLink));
        when(playerProfileRepository.existsByLinkedUser_IdAndTeam_Id(parentId, teamId)).thenReturn(false);
        when(playerProfileRepository.existsByLinkedUser_IdAndTeam_Id(otherParent.getId(), teamId)).thenReturn(true);

        assertThatThrownBy(() -> teamInviteService.acceptInvite(
                "invite-token",
                parent,
                new AcceptInviteRequest(null, null, null, null, profileId)))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.team.role_conflict");

        verify(teamService, never()).addProfileToTeam(any(), any());
    }

    private TeamInvite parentInvite(Team team) {
        TeamInvite invite = new TeamInvite();
        invite.setId(UUID.randomUUID());
        invite.setCreatedByUserId(UUID.randomUUID());
        invite.setTeam(team);
        invite.setInvitedRole(UserRole.PARENT);
        invite.setInviteKind(InviteKind.PARENT);
        invite.setToken("invite-token");
        invite.setMaxUses(20);
        invite.setUsedCount(0);
        invite.setExpiresAt(LocalDateTime.now().plusHours(2));
        return invite;
    }

    private PlayerProfile childProfile(UUID profileId) {
        PlayerProfile profile = new PlayerProfile();
        profile.setId(profileId);
        profile.setFirstName("Peti");
        profile.setLastName("Teszt");
        return profile;
    }


    @Test
    void generateInviteToken_parentHatOnFanStampCanInviteFan() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Fan fan = new Fan();
        fan.setId(userId);
        fan.setEmail("fan@test.com");
        fan.setRole(UserRole.FAN);
        Team team = teamNamed(teamId);
        authenticate("FAN", "PARENT");

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerParentRepository.existsByUser_IdAndPlayerProfile_Team_IdIn(userId, List.of(teamId))).thenReturn(true);
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InviteTokenResponse response = teamInviteService.generateInviteToken(teamId, fan, UserRole.FAN, UserRole.PARENT);

        assertThat(response.token()).isNotBlank();
        ArgumentCaptor<TeamInvite> inviteCaptor = ArgumentCaptor.forClass(TeamInvite.class);
        verify(teamInviteRepository).save(inviteCaptor.capture());
        assertThat(inviteCaptor.getValue().getCreatedByUserRole()).isEqualTo(UserRole.PARENT);
    }

    @Test
    void generateInviteToken_parentHatWithoutChildIsRejected() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Fan fan = new Fan();
        fan.setId(userId);
        fan.setRole(UserRole.FAN);
        authenticate("FAN", "PARENT");

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamNamed(teamId)));
        when(playerParentRepository.existsByUser_IdAndPlayerProfile_Team_IdIn(userId, List.of(teamId))).thenReturn(false);

        assertThatThrownBy(() -> teamInviteService.generateInviteToken(teamId, fan, UserRole.FAN, UserRole.PARENT))
                .isInstanceOf(ConflictException.class);
        verify(teamInviteRepository, never()).save(any());
    }

    @Test
    void generateInviteToken_coachWhoIsClubAdminCanInviteWithoutCoaching() {
        UUID teamId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        UUID coachId = UUID.randomUUID();
        Coach coach = createCoach(coachId, "coach@test.com");
        Club club = new Club();
        club.setId(clubId);
        Team team = teamNamed(teamId);
        team.setClub(club);
        authenticate("COACH", "CLUB_ADMIN");

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(userAccessService.isClubAdmin(coach, clubId)).thenReturn(true);
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InviteTokenResponse response = teamInviteService.generateInviteToken(teamId, coach, UserRole.COACH, UserRole.CLUB_ADMIN);

        assertThat(response.token()).isNotBlank();
    }

    @Test
    void generateInviteToken_coachWhoIsOnlyParentHereCanInviteFansOnly() {
        UUID teamId = UUID.randomUUID();
        UUID coachId = UUID.randomUUID();
        Coach coach = createCoach(coachId, "coach@test.com");
        authenticate("COACH", "PARENT");

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamNamed(teamId)));
        when(playerParentRepository.existsByUser_IdAndPlayerProfile_Team_IdIn(coachId, List.of(teamId))).thenReturn(true);
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InviteTokenResponse response = teamInviteService.generateInviteToken(teamId, coach, UserRole.FAN, UserRole.PARENT);
        assertThat(response.token()).isNotBlank();

        assertThatThrownBy(() -> teamInviteService.generateInviteToken(teamId, coach, UserRole.PLAYER, UserRole.PARENT))
                .isInstanceOf(BadRequestException.class);
    }


    @Test
    void generateInviteToken_openViewLimitsAdminWhoIsAlsoPlayer() {
        UUID teamId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Player player = createPlayer(playerId, "player@test.com");
        authenticate("ADMIN", "PLAYER");

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamNamed(teamId)));
        when(playerProfileRepository.existsByLinkedUser_IdAndTeam_Id(playerId, teamId)).thenReturn(true);
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> teamInviteService.generateInviteToken(teamId, player, UserRole.PLAYER, UserRole.PLAYER))
                .isInstanceOf(BadRequestException.class);

        InviteTokenResponse response = teamInviteService.generateInviteToken(teamId, player, UserRole.PLAYER, UserRole.ADMIN);
        assertThat(response.token()).isNotBlank();
    }

    private void authenticate(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "actor",
                "n/a",
                List.of(roles).stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()));
    }

    private Team teamNamed(UUID teamId) {
        Team team = new Team();
        team.setId(teamId);
        team.setName("Arsenal");
        return team;
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
