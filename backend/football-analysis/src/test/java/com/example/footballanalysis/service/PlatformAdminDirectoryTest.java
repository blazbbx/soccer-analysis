package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformAdminDirectoryTest {

    @Mock
    private KeycloakUserAdminService keycloakUserAdminService;

    private final AtomicLong now = new AtomicLong(1_000L);

    private PlatformAdminDirectory directory() {
        return new PlatformAdminDirectory(keycloakUserAdminService, now::get);
    }

    @Test
    void readsTheAdminsFromKeycloakOnceAndSkipsIdsThatAreNotOurs() {
        UUID admin = UUID.randomUUID();
        when(keycloakUserAdminService.listUserIdsWithClientRole(UserRole.ADMIN))
                .thenReturn(Arrays.asList(admin.toString(), "service-account", "", null));
        PlatformAdminDirectory directory = directory();

        assertThat(directory.adminIdsForDisplay()).containsExactly(admin);
        assertThat(directory.adminIdsForDisplay()).containsExactly(admin);

        verify(keycloakUserAdminService, times(1)).listUserIdsWithClientRole(UserRole.ADMIN);
    }

    @Test
    void asksKeycloakAgainOnlyAfterTheCacheExpires() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(keycloakUserAdminService.listUserIdsWithClientRole(UserRole.ADMIN))
                .thenReturn(List.of(first.toString()))
                .thenReturn(List.of(second.toString()));
        PlatformAdminDirectory directory = directory();

        assertThat(directory.adminIdsForDisplay()).containsExactly(first);
        now.addAndGet(PlatformAdminDirectory.CACHE_NANOS - 1);
        assertThat(directory.adminIdsForDisplay()).containsExactly(first);
        now.addAndGet(1);
        assertThat(directory.adminIdsForDisplay()).containsExactly(second);

        verify(keycloakUserAdminService, times(2)).listUserIdsWithClientRole(UserRole.ADMIN);
    }

    @Test
    void anOutageKeepsTheLastListAndRetriesOnlyAfterTheWait() {
        UUID admin = UUID.randomUUID();
        when(keycloakUserAdminService.listUserIdsWithClientRole(UserRole.ADMIN))
                .thenReturn(List.of(admin.toString()))
                .thenThrow(new IllegalStateException("Keycloak is down"))
                .thenReturn(List.of());
        PlatformAdminDirectory directory = directory();

        assertThat(directory.adminIdsForDisplay()).containsExactly(admin);
        now.addAndGet(PlatformAdminDirectory.CACHE_NANOS);
        // The failed call keeps the last labels...
        assertThat(directory.adminIdsForDisplay()).containsExactly(admin);
        // ...and every request during the wait is served without calling Keycloak.
        now.addAndGet(PlatformAdminDirectory.RETRY_AFTER_FAILURE_NANOS - 1);
        assertThat(directory.adminIdsForDisplay()).containsExactly(admin);
        verify(keycloakUserAdminService, times(2)).listUserIdsWithClientRole(UserRole.ADMIN);

        now.addAndGet(1);
        assertThat(directory.adminIdsForDisplay()).isEmpty();
        verify(keycloakUserAdminService, times(3)).listUserIdsWithClientRole(UserRole.ADMIN);
    }

    @Test
    void anUnreachableKeycloakAtStartGivesNoLabelsInsteadOfAnError() {
        when(keycloakUserAdminService.listUserIdsWithClientRole(UserRole.ADMIN))
                .thenThrow(new IllegalStateException("Keycloak is down"));
        PlatformAdminDirectory directory = directory();

        assertThat(directory.adminIdsForDisplay()).isEmpty();
        assertThat(directory.adminIdsForDisplay()).isEmpty();

        verify(keycloakUserAdminService, times(1)).listUserIdsWithClientRole(UserRole.ADMIN);
    }
}
