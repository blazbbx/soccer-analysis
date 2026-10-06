package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
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
    private EntityManager entityManager;

    @Mock
    private KeycloakUserAdminService keycloakUserAdminService;

    @Mock
    private Query nativeQuery;

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
        assertThat(saved).isInstanceOf(Player.class);
        assertThat(saved.getId()).isEqualTo(userId);
        assertThat(saved.getEmail()).isEqualTo("player@test.com");
        assertThat(saved.getFirstName()).isEqualTo("Pat");
        assertThat(saved.getLastName()).isEqualTo("Player");
        assertThat(saved.getRole()).isEqualTo(UserRole.PLAYER);
        verify(entityManager, never()).createNativeQuery(anyString());
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
    void doesNothingWhenDbRoleAlreadyMatchesJwt() {
        UUID userId = UUID.randomUUID();
        Player existing = new Player();
        existing.setId(userId);
        existing.setRole(UserRole.PLAYER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));

        userRoleSyncService.syncFromJwt(jwt(userId, "player"));

        verify(userRepository, never()).save(any());
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    void promotesStampWhenJwtAddsAHigherHat() {
        UUID userId = UUID.randomUUID();
        Player existing = new Player();
        existing.setId(userId);
        existing.setRole(UserRole.PLAYER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        when(nativeQuery.executeUpdate()).thenReturn(1);

        userRoleSyncService.syncFromJwt(jwt(userId, "player", "coach"));

        verify(nativeQuery).setParameter("userType", "COACH");
        verify(nativeQuery).setParameter("role", "COACH");
        verify(entityManager).clear();
        verify(userRepository, never()).save(any());
    }

    @Test
    void keepsStampWhenJwtAddsOnlyALowerHat() {
        UUID userId = UUID.randomUUID();
        com.example.footballanalysis.model.db.user.Coach existing = new com.example.footballanalysis.model.db.user.Coach();
        existing.setId(userId);
        existing.setRole(UserRole.COACH);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));

        userRoleSyncService.syncFromJwt(jwt(userId, "coach", "fan"));

        verify(userRepository, never()).save(any());
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    void promotesCoachStampToAdminWhileCoachHatRemains() {
        UUID userId = UUID.randomUUID();
        com.example.footballanalysis.model.db.user.Coach existing = new com.example.footballanalysis.model.db.user.Coach();
        existing.setId(userId);
        existing.setRole(UserRole.COACH);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        when(nativeQuery.executeUpdate()).thenReturn(1);

        userRoleSyncService.syncFromJwt(jwt(userId, "coach", "admin"));

        verify(nativeQuery).setParameter("userType", "ADMIN");
        verify(nativeQuery).setParameter("role", "ADMIN");
        verify(entityManager).clear();
    }

    @Test
    void skipsJitWhenJwtHasNoKnownHats() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        userRoleSyncService.syncFromJwt(jwt(userId));

        verify(keycloakUserAdminService, never()).existsUser(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updatesDiscriminatorWithoutCopyingMembershipsWhenStampLeavesTheToken() {
        UUID userId = UUID.randomUUID();
        Player existing = new Player();
        existing.setId(userId);
        existing.setRole(UserRole.PLAYER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        when(nativeQuery.executeUpdate()).thenReturn(1);

        userRoleSyncService.syncFromJwt(jwt(userId, "coach"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, times(1)).createNativeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("UPDATE users SET user_type = :userType");
        assertThat(sqlCaptor.getValue()).doesNotContain("INSERT INTO");
        assertThat(sqlCaptor.getValue()).doesNotContain("DELETE FROM");
        verify(entityManager).clear();
    }

    @Test
    void updatesStampToAdminWithoutDeletingMembershipRows() {
        UUID userId = UUID.randomUUID();
        Player existing = new Player();
        existing.setId(userId);
        existing.setRole(UserRole.PLAYER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);

        userRoleSyncService.syncFromJwt(jwt(userId, "admin"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, times(1)).createNativeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("UPDATE users SET user_type = :userType");
        assertThat(sqlCaptor.getValue()).doesNotContain("DELETE FROM");
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
