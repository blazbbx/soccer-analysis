package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.service.InitialAdminBootstrap.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InitialAdminBootstrap")
class InitialAdminBootstrapTest {

    private static final String EMAIL = "first.admin@example.com";
    private static final String PASSWORD = "starting-password";

    @Mock private KeycloakUserAdminService keycloak;

    private InitialAdminBootstrap bootstrap(String email, String password) {
        return new InitialAdminBootstrap(keycloak, email, password);
    }

    @Test
    void createsAdminAndRequiresPasswordChange_whenNoAdminExists() {
        when(keycloak.listUserIdsWithClientRole(UserRole.ADMIN)).thenReturn(List.of());
        when(keycloak.createUser(EMAIL, "Platform", "Admin", PASSWORD, UserRole.ADMIN)).thenReturn("kc-1");

        assertThat(bootstrap(EMAIL, PASSWORD).createIfMissing()).isEqualTo(Outcome.CREATED);

        verify(keycloak).requirePasswordChange("kc-1");
    }

    @Test
    void trimsTheEmail() {
        when(keycloak.listUserIdsWithClientRole(UserRole.ADMIN)).thenReturn(List.of());
        when(keycloak.createUser(EMAIL, "Platform", "Admin", PASSWORD, UserRole.ADMIN)).thenReturn("kc-1");

        assertThat(bootstrap("  " + EMAIL + " ", PASSWORD).createIfMissing()).isEqualTo(Outcome.CREATED);
    }

    @Test
    void doesNothing_whenAnAdminAlreadyExists() {
        when(keycloak.listUserIdsWithClientRole(UserRole.ADMIN)).thenReturn(List.of("existing-admin"));

        assertThat(bootstrap(EMAIL, PASSWORD).createIfMissing()).isEqualTo(Outcome.ADMIN_EXISTS);

        verify(keycloak, never()).createUser(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void doesNotTouchKeycloak_whenEmailOrPasswordIsMissing() {
        assertThat(bootstrap("", PASSWORD).createIfMissing()).isEqualTo(Outcome.NOT_CONFIGURED);
        assertThat(bootstrap(EMAIL, "").createIfMissing()).isEqualTo(Outcome.NOT_CONFIGURED);
        assertThat(bootstrap(null, null).createIfMissing()).isEqualTo(Outcome.NOT_CONFIGURED);

        bootstrap("", "").onReady();
        bootstrap(EMAIL, "").onReady();

        verifyNoInteractions(keycloak);
    }

    @Test
    void doesNotPromoteAnExistingAccount_whenTheEmailIsTaken() {
        when(keycloak.listUserIdsWithClientRole(UserRole.ADMIN)).thenReturn(List.of());
        when(keycloak.createUser(EMAIL, "Platform", "Admin", PASSWORD, UserRole.ADMIN))
                .thenThrow(new ConflictException("error.user.email.conflict", new Object[]{EMAIL}, "taken"));

        assertThat(bootstrap(EMAIL, PASSWORD).createIfMissing()).isEqualTo(Outcome.EMAIL_TAKEN);

        verify(keycloak, never()).addClientRole(anyString(), any());
        verify(keycloak, never()).requirePasswordChange(anyString());
        verify(keycloak, never()).findUserIdByEmail(anyString());
    }

    @Test
    void finishesItsOwnAccount_whenAnEarlierCreateGotNoAnswer() {
        when(keycloak.listUserIdsWithClientRole(UserRole.ADMIN)).thenReturn(List.of());
        // The first create reaches Keycloak, but its answer times out; the retry finds the e-mail taken.
        when(keycloak.createUser(EMAIL, "Platform", "Admin", PASSWORD, UserRole.ADMIN))
                .thenThrow(new IllegalStateException("read timed out"))
                .thenThrow(new ConflictException("error.user.email.conflict", new Object[]{EMAIL}, "taken"));
        when(keycloak.findUserIdByEmail(EMAIL)).thenReturn(java.util.Optional.of("kc-1"));
        InitialAdminBootstrap bootstrap = bootstrap(EMAIL, PASSWORD);

        assertThatThrownBy(bootstrap::createIfMissing).isInstanceOf(IllegalStateException.class);
        assertThat(bootstrap.createIfMissing()).isEqualTo(Outcome.CREATED);

        verify(keycloak).addClientRole("kc-1", UserRole.ADMIN);
        verify(keycloak).requirePasswordChange("kc-1");
    }

    @Test
    void staysCreated_whenTheForcedPasswordChangeFails() {
        when(keycloak.listUserIdsWithClientRole(UserRole.ADMIN)).thenReturn(List.of());
        when(keycloak.createUser(EMAIL, "Platform", "Admin", PASSWORD, UserRole.ADMIN)).thenReturn("kc-1");
        doThrow(new IllegalStateException("keycloak down")).when(keycloak).requirePasswordChange("kc-1");

        assertThat(bootstrap(EMAIL, PASSWORD).createIfMissing()).isEqualTo(Outcome.CREATED);
    }

    @Test
    void lets_theCallerRetry_whenKeycloakIsNotReachable() {
        when(keycloak.listUserIdsWithClientRole(UserRole.ADMIN)).thenThrow(new IllegalStateException("connection refused"));

        assertThatThrownBy(() -> bootstrap(EMAIL, PASSWORD).createIfMissing())
                .isInstanceOf(IllegalStateException.class);
    }
}
