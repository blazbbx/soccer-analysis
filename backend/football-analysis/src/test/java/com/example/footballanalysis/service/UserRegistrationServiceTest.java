package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.FieldConflictException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.TeamInvite;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.RegisterUserRequest;
import com.example.footballanalysis.model.responses.UserResponse;
import com.example.footballanalysis.repository.TeamInviteRepository;
import com.example.footballanalysis.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserRegistrationService tesztek")
class UserRegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamInviteRepository teamInviteRepository;

    @Mock
    private TeamService teamService;

    @Mock
    private KeycloakUserAdminService keycloakUserAdminService;

    @Mock
    private AuditEventService auditEventService;

    private UserRegistrationService createService() {
        return new UserRegistrationService(userRepository, teamInviteRepository, teamService, keycloakUserAdminService, auditEventService);
    }

    @Test
    void registerWithInvite_createsPlayerAndLinksTeam() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String keycloakId = userId.toString();
        TeamInvite invite = activeInvite(teamId, UserRole.PLAYER);

        RegisterUserRequest request = request("player@test.com", "Peter", "Parker", "secret123", invite.getToken());
        when(teamInviteRepository.findByToken(invite.getToken())).thenReturn(Optional.of(invite));
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        when(keycloakUserAdminService.createUser(request.email(), request.firstName(), request.lastName(), request.password(), UserRole.PLAYER))
                .thenReturn(keycloakId);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setCreatedAt(LocalDateTime.of(2026, 3, 22, 12, 0));
            return user;
        });
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = createService().registerWithInvite(request);

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("player@test.com");
        assertThat(response.firstName()).isEqualTo("Peter");
        assertThat(response.lastName()).isEqualTo("Parker");
        assertThat(response.role()).isEqualTo(UserRole.PLAYER);
        assertThat(response.teams()).isEmpty();
        verify(teamService).addPlayerToTeam(teamId, userId);
        verify(teamInviteRepository).save(invite);
        verify(auditEventService).record(
            org.mockito.ArgumentMatchers.eq("TEAM_INVITE_ACCEPTED"),
            org.mockito.ArgumentMatchers.eq(userId),
            org.mockito.ArgumentMatchers.eq(UserRole.PLAYER.name()),
            org.mockito.ArgumentMatchers.eq("TEAM"),
            org.mockito.ArgumentMatchers.eq(teamId.toString()),
            org.mockito.ArgumentMatchers.contains("flow=registration")
        );
    }

    @Test
    void registerWithInvite_createsCoachAndLinksTeam() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String keycloakId = userId.toString();
        TeamInvite invite = activeInvite(teamId, UserRole.COACH);

        RegisterUserRequest request = request("coach@test.com", "Bruce", "Wayne", "secret123", invite.getToken());
        when(teamInviteRepository.findByToken(invite.getToken())).thenReturn(Optional.of(invite));
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        when(keycloakUserAdminService.createUser(request.email(), request.firstName(), request.lastName(), request.password(), UserRole.COACH))
                .thenReturn(keycloakId);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setCreatedAt(LocalDateTime.of(2026, 3, 22, 12, 0));
            return user;
        });
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = createService().registerWithInvite(request);

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.role()).isEqualTo(UserRole.COACH);
        verify(teamService).addCoachToTeam(teamId, userId);
        verify(teamInviteRepository).save(invite);
        verify(auditEventService).record(
            org.mockito.ArgumentMatchers.eq("TEAM_INVITE_ACCEPTED"),
            org.mockito.ArgumentMatchers.eq(userId),
            org.mockito.ArgumentMatchers.eq(UserRole.COACH.name()),
            org.mockito.ArgumentMatchers.eq("TEAM"),
            org.mockito.ArgumentMatchers.eq(teamId.toString()),
            org.mockito.ArgumentMatchers.contains("flow=registration")
        );
    }

    @Test
    void registerWithInvite_createsFanAndLinksTeam() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String keycloakId = userId.toString();
        TeamInvite invite = activeInvite(teamId, UserRole.FAN);

        RegisterUserRequest request = request("fan@test.com", "Tony", "Stark", "secret123", invite.getToken());
        when(teamInviteRepository.findByToken(invite.getToken())).thenReturn(Optional.of(invite));
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        when(keycloakUserAdminService.createUser(request.email(), request.firstName(), request.lastName(), request.password(), UserRole.FAN))
                .thenReturn(keycloakId);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setCreatedAt(LocalDateTime.of(2026, 3, 22, 12, 0));
            return user;
        });
        when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = createService().registerWithInvite(request);

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.role()).isEqualTo(UserRole.FAN);
        verify(teamService).addFanToTeam(teamId, userId);
        verify(teamInviteRepository).save(invite);
        verify(auditEventService).record(
            org.mockito.ArgumentMatchers.eq("TEAM_INVITE_ACCEPTED"),
            org.mockito.ArgumentMatchers.eq(userId),
            org.mockito.ArgumentMatchers.eq(UserRole.FAN.name()),
            org.mockito.ArgumentMatchers.eq("TEAM"),
            org.mockito.ArgumentMatchers.eq(teamId.toString()),
            org.mockito.ArgumentMatchers.contains("flow=registration")
        );
    }

    @Test
    void registerWithInvite_rejectsMissingInvite() {
        RegisterUserRequest request = request("player@test.com", "Peter", "Parker", "secret123", "missing-token");
        when(teamInviteRepository.findByToken("missing-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createService().registerWithInvite(request))
                .isInstanceOf(NotFoundException.class);

        verify(keycloakUserAdminService, never()).createUser(any(), any(), any(), any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerWithInvite_rejectsExpiredInvite() {
        TeamInvite invite = expiredInvite(UserRole.PLAYER);
        RegisterUserRequest request = request("player@test.com", "Peter", "Parker", "secret123", invite.getToken());
        when(teamInviteRepository.findByToken(invite.getToken())).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> createService().registerWithInvite(request))
                .isInstanceOf(BadRequestException.class);

        verify(keycloakUserAdminService, never()).createUser(any(), any(), any(), any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerWithInvite_rejectsExhaustedInvite() {
        TeamInvite invite = exhaustedInvite(UserRole.PLAYER);
        RegisterUserRequest request = request("player@test.com", "Peter", "Parker", "secret123", invite.getToken());
        when(teamInviteRepository.findByToken(invite.getToken())).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> createService().registerWithInvite(request))
                .isInstanceOf(BadRequestException.class);

        verify(keycloakUserAdminService, never()).createUser(any(), any(), any(), any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerWithInvite_rejectsDuplicateEmail() {
        TeamInvite invite = activeInvite(UUID.randomUUID(), UserRole.PLAYER);
        RegisterUserRequest request = request("player@test.com", "Peter", "Parker", "secret123", invite.getToken());
        when(teamInviteRepository.findByToken(invite.getToken())).thenReturn(Optional.of(invite));
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(existingPlayer(request.email())));

        assertThatThrownBy(() -> createService().registerWithInvite(request))
                .isInstanceOf(FieldConflictException.class);

        verify(keycloakUserAdminService, never()).createUser(any(), any(), any(), any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerWithInvite_rejectsAdminInviteAndCleansUpKeycloakUser() {
        TeamInvite invite = activeInvite(UUID.randomUUID(), UserRole.ADMIN);
        RegisterUserRequest request = request("admin@test.com", "Ada", "Lovelace", "secret123", invite.getToken());
        when(teamInviteRepository.findByToken(invite.getToken())).thenReturn(Optional.of(invite));
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        when(keycloakUserAdminService.createUser(request.email(), request.firstName(), request.lastName(), request.password(), UserRole.ADMIN))
                .thenReturn("kc-admin-id");

        assertThatThrownBy(() -> createService().registerWithInvite(request))
                .isInstanceOf(ConflictException.class);

        verify(keycloakUserAdminService).deleteUser("kc-admin-id");
        verify(userRepository, never()).save(any());
        verify(teamService, never()).addPlayerToTeam(any(), any());
        verify(teamService, never()).addCoachToTeam(any(), any());
        verify(teamService, never()).addFanToTeam(any(), any());
    }

    @Test
    void registerWithInvite_cleansUpWhenLocalSaveFails() {
        TeamInvite invite = activeInvite(UUID.randomUUID(), UserRole.PLAYER);
        RegisterUserRequest request = request("player@test.com", "Peter", "Parker", "secret123", invite.getToken());
        when(teamInviteRepository.findByToken(invite.getToken())).thenReturn(Optional.of(invite));
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        String keycloakId = UUID.randomUUID().toString();
        when(keycloakUserAdminService.createUser(request.email(), request.firstName(), request.lastName(), request.password(), UserRole.PLAYER))
            .thenReturn(keycloakId);
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("DB failed"));

        assertThatThrownBy(() -> createService().registerWithInvite(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB failed");

        verify(keycloakUserAdminService).deleteUser(keycloakId);
        verify(teamService, never()).addPlayerToTeam(any(), any());
        verify(teamInviteRepository, never()).save(any());
    }

    @Test
    void registerWithInvite_cleansUpWhenTeamAssignmentFails() {
        TeamInvite invite = activeInvite(UUID.randomUUID(), UserRole.PLAYER);
        RegisterUserRequest request = request("player@test.com", "Peter", "Parker", "secret123", invite.getToken());
        when(teamInviteRepository.findByToken(invite.getToken())).thenReturn(Optional.of(invite));
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        String keycloakId = UUID.randomUUID().toString();
        when(keycloakUserAdminService.createUser(request.email(), request.firstName(), request.lastName(), request.password(), UserRole.PLAYER))
            .thenReturn(keycloakId);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.fromString(keycloakId));
            user.setCreatedAt(LocalDateTime.of(2026, 3, 22, 12, 0));
            return user;
        });
        org.mockito.Mockito.doThrow(new RuntimeException("team mapping failed"))
                .when(teamService).addPlayerToTeam(any(), any());

        assertThatThrownBy(() -> createService().registerWithInvite(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("team mapping failed");

        verify(keycloakUserAdminService).deleteUser(keycloakId);
        verify(teamInviteRepository, never()).save(any());
    }

    @Test
    void registerWithInvite_doesNotCleanupWhenKeycloakCreationFails() {
        TeamInvite invite = activeInvite(UUID.randomUUID(), UserRole.PLAYER);
        RegisterUserRequest request = request("player@test.com", "Peter", "Parker", "secret123", invite.getToken());
        when(teamInviteRepository.findByToken(invite.getToken())).thenReturn(Optional.of(invite));
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        when(keycloakUserAdminService.createUser(request.email(), request.firstName(), request.lastName(), request.password(), UserRole.PLAYER))
                .thenThrow(new RuntimeException("Keycloak unavailable"));

        assertThatThrownBy(() -> createService().registerWithInvite(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Keycloak unavailable");

        verify(keycloakUserAdminService, never()).deleteUser(any());
        verify(userRepository, never()).save(any());
        verify(teamService, never()).addPlayerToTeam(any(), any());
    }

    private RegisterUserRequest request(String email, String firstName, String lastName, String password, String inviteToken) {
        return new RegisterUserRequest(email, firstName, lastName, password, inviteToken);
    }

    private TeamInvite activeInvite(UUID teamId, UserRole role) {
        TeamInvite invite = new TeamInvite();
        invite.setToken(UUID.randomUUID().toString());
        invite.setTeam(team(teamId, "Arsenal"));
        invite.setInvitedRole(role);
        invite.setMaxUses(5);
        invite.setUsedCount(0);
        invite.setExpiresAt(LocalDateTime.now().plusHours(1));
        return invite;
    }

    private TeamInvite expiredInvite(UserRole role) {
        TeamInvite invite = activeInvite(UUID.randomUUID(), role);
        invite.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        return invite;
    }

    private TeamInvite exhaustedInvite(UserRole role) {
        TeamInvite invite = activeInvite(UUID.randomUUID(), role);
        invite.setUsedCount(invite.getMaxUses());
        return invite;
    }

    private Team team(UUID id, String name) {
        Team team = new Team();
        team.setId(id);
        team.setName(name);
        return team;
    }

    private Player existingPlayer(String email) {
        Player player = new Player();
        player.setId(UUID.randomUUID());
        player.setEmail(email);
        player.setFirstName("Existing");
        player.setLastName("Player");
        player.setRole(UserRole.PLAYER);
        return player;
    }
}


