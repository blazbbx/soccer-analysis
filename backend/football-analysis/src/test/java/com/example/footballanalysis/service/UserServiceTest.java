package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.Admin;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.UpdateUserRequest;
import com.example.footballanalysis.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService update tesztek")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KeycloakUserAdminService keycloakUserAdminService;

    @Mock
    private AuditEventService auditEventService;

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

        UpdateUserRequest req = new UpdateUserRequest("New", "Surname");

        var response = userService.updateUser(userId, req);

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
}