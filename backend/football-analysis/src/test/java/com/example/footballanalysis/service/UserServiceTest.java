package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.user.Admin;
import com.example.footballanalysis.model.db.user.Fan;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.UpdateUserRequest;
import com.example.footballanalysis.repository.ClipRepository;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.MatchSquadMemberRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.TeamMessageRepository;
import com.example.footballanalysis.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService update tesztek")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamMessageRepository teamMessageRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private ClipRepository clipRepository;

    @Mock
    private MatchSquadMemberRepository matchSquadMemberRepository;

    @Mock
    private KeycloakUserAdminService keycloakUserAdminService;

    @Mock
    private AuditEventService auditEventService;

    @Mock
    private PlayerProfileRepository playerProfileRepository;

    @Mock
    private PlayerParentService playerParentService;

    @InjectMocks
    private UserService userService;

    @Test
    void getAllUsers_returnsEmptyListWhenRepositoryReturnsNull() {
        when(userRepository.findAll()).thenReturn(null);

        var users = userService.getAllUsers();

        assertThat(users).isEmpty();
    }

    @Test
    void updateUser_updatesUserInDbAndKeycloak() {
        UUID userId = UUID.randomUUID();

        Admin user = new Admin();
        user.setId(userId);
        user.setEmail("admin@test.com");
        user.setFirstName("Old");
        user.setLastName("Name");
        user.setRole(UserRole.ADMIN);
        user.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("sub", userId.toString())
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(3600))
                .build();

        UpdateUserRequest req = new UpdateUserRequest("New", "Surname");

        var response = userService.updateUser(userId, req, jwt);

        verify(keycloakUserAdminService).updateUser(userId.toString(), "New", "Surname");

        ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFirstName()).isEqualTo("New");
        assertThat(captor.getValue().getLastName()).isEqualTo("Surname");
        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.firstName()).isEqualTo("New");
        assertThat(response.lastName()).isEqualTo("Surname");
    }

    @Test
    void updateMyUser_usesJwtSubjectAsCurrentUserId() {
        UUID userId = UUID.randomUUID();

        Admin user = new Admin();
        user.setId(userId);
        user.setEmail("admin@test.com");
        user.setFirstName("Old");
        user.setLastName("Name");
        user.setRole(UserRole.ADMIN);
        user.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("sub", userId.toString())
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(3600))
                .build();

        UpdateUserRequest req = new UpdateUserRequest("Self", "Update");

        var response = userService.updateMyUser(req, jwt);

        verify(keycloakUserAdminService).updateUser(userId.toString(), "Self", "Update");
        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.firstName()).isEqualTo("Self");
        assertThat(response.lastName()).isEqualTo("Update");
    }

    @Test
    void updateMyUser_rejectsMissingSubject() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(3600))
                .build();

        assertThatThrownBy(() -> userService.updateMyUser(new UpdateUserRequest("A", "B"), jwt))
                .isInstanceOf(com.example.footballanalysis.exception.BadRequestException.class);
    }

    @Test
    void deleteUser_clearsChatAndOtherReferencesBeforeDeletingUser() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Jwt jwt = jwtFor(actorId);

        Fan fan = new Fan();
        fan.setId(userId);
        fan.setRole(UserRole.FAN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(fan));

        userService.deleteUser(userId, jwt);

        InOrder order = inOrder(
                teamMessageRepository,
                matchRepository,
                clipRepository,
                matchSquadMemberRepository,
                playerParentService,
                userRepository,
                keycloakUserAdminService
        );
        order.verify(teamMessageRepository).deleteBySenderId(userId);
        order.verify(matchRepository).clearUploadedByUserId(userId);
        order.verify(clipRepository).clearCreatedByUserId(userId);
        order.verify(matchSquadMemberRepository).deleteAllByPlayer_Id(userId);
        order.verify(playerParentService).detachUser(userId);
        order.verify(userRepository).deleteById(userId);
        order.verify(userRepository).flush();
        order.verify(keycloakUserAdminService).deleteUser(userId.toString());
        verify(auditEventService).record(
                "USER_DELETED",
                actorId,
                null,
                "USER",
                userId.toString(),
                "deleted_from_local_db=true, deleted_from_keycloak=true"
        );
    }

    @Test
    void deleteUser_throwsWhenUserMissing() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(userId, jwtFor(UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class);

        verify(teamMessageRepository, never()).deleteBySenderId(any());
        verify(userRepository, never()).deleteById(any());
        verify(keycloakUserAdminService, never()).deleteUser(any());
    }

    @Test
    void deleteUser_rejectsLastRemainingAdmin() {
        UUID adminId = UUID.randomUUID();
        Admin admin = new Admin();
        admin.setId(adminId);
        admin.setRole(UserRole.ADMIN);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.deleteUser(adminId, jwtFor(UUID.randomUUID())))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.user.last_admin");

        verify(userRepository, never()).deleteById(any());
        verify(keycloakUserAdminService, never()).deleteUser(any());
        verify(teamMessageRepository, never()).deleteBySenderId(any());
    }

    @Test
    void deleteUser_allowsAdminWhenAnotherAdminExists() {
        UUID adminId = UUID.randomUUID();
        Admin admin = new Admin();
        admin.setId(adminId);
        admin.setRole(UserRole.ADMIN);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(2L);

        userService.deleteUser(adminId, jwtFor(UUID.randomUUID()));

        verify(userRepository).deleteById(adminId);
        verify(keycloakUserAdminService).deleteUser(adminId.toString());
    }

    @Test
    void deleteUser_rejectsDeletingOwnAccount() {
        UUID adminId = UUID.randomUUID();
        Admin admin = new Admin();
        admin.setId(adminId);
        admin.setRole(UserRole.ADMIN);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.deleteUser(adminId, jwtFor(adminId)))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.user.self_delete");

        verify(userRepository, never()).countByRole(any());
        verify(userRepository, never()).deleteById(any());
        verify(keycloakUserAdminService, never()).deleteUser(any());
    }

    private static Jwt jwtFor(UUID userId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("sub", userId.toString())
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(3600))
                .build();
    }
}