package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserRoleSyncService")
class UserRoleSyncServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KeycloakUserAdminService keycloakUserAdminService;

    @InjectMocks
    private UserRoleSyncService userRoleSyncService;

    @Test
    void skipsWhenSubjectIsNotUuid() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("not-a-uuid")
                .claim("email", "player@test.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        userRoleSyncService.syncFromJwt(jwt);

        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void provisionsMissingUserFromJwt() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = jwt(userId, "player");
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(keycloakUserAdminService.existsUser(userId.toString())).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userRoleSyncService.syncFromJwt(jwt);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getId()).isEqualTo(userId);
        assertThat(saved.getEmail()).isEqualTo("player@test.com");
        assertThat(saved.getFirstName()).isEqualTo("Pat");
        assertThat(saved.getLastName()).isEqualTo("Player");
    }

    @Test
    void skipsProvisioningWhenKeycloakUserIsGone() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(keycloakUserAdminService.existsUser(userId.toString())).thenReturn(false);

        userRoleSyncService.syncFromJwt(jwt(userId, "admin"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void localUserExists_returnsTrueWhenRepositoryHasUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(true);

        assertThat(userRoleSyncService.localUserExists(jwt(userId, "admin"))).isTrue();
    }

    @Test
    void anExistingUserIsKeptAsItIsWhateverHatsTheTokenCarries() {
        // The roles live only in the token: the local row has no copy to update.
        UUID userId = UUID.randomUUID();
        User existing = new User();
        existing.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));

        userRoleSyncService.syncFromJwt(jwt(userId, "player", "coach", "admin"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void skipsJitWhenJwtHasNoKnownHats() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        userRoleSyncService.syncFromJwt(jwt(userId));

        verify(keycloakUserAdminService, never()).existsUser(anyString());
        verify(userRepository, never()).save(any());
    }

    private static Jwt jwt(UUID userId, String... roles) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("email", "player@test.com")
                .claim("given_name", "Pat")
                .claim("family_name", "Player")
                .claim("azp", "football-web-client")
                .claim("resource_access", Map.of(
                        "football-web-client", Map.of("roles", List.of(roles))))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
