package com.example.footballanalysis.controller;

import com.example.footballanalysis.config.SecurityConfig;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.service.SseNotificationService;
import com.example.footballanalysis.service.UserAccessService;
import com.example.footballanalysis.service.UserRoleSyncService;
import com.example.footballanalysis.util.KeycloakRoleConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Locale;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class)
@Import({SecurityConfig.class, KeycloakRoleConverter.class, NotificationControllerAclWebMvcTest.MockConfig.class})
@DisplayName("NotificationController ACL (security filters on)")
class NotificationControllerAclWebMvcTest {

    @TestConfiguration
    static class MockConfig {
        @Bean SseNotificationService sseNotificationService() { return Mockito.mock(SseNotificationService.class); }
        @Bean UserAccessService userAccessService() { return Mockito.mock(UserAccessService.class); }
        @Bean UserRoleSyncService userRoleSyncService() {
            UserRoleSyncService mock = Mockito.mock(UserRoleSyncService.class);
            given(mock.localUserExists(any())).willReturn(true);
            return mock;
        }
        @Bean LocaleResolver localeResolver() { return new FixedLocaleResolver(Locale.ENGLISH); }
    }

    @BeforeAll
    static void forceEnglishLocale() {
        Locale.setDefault(Locale.ENGLISH);
    }

    @Autowired MockMvc mockMvc;
    @Autowired SseNotificationService sseService;
    @Autowired UserAccessService userAccessService;

    @Test
    void subscribe_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/notifications/subscribe/{matchId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void subscribe_coach_returns200() throws Exception {
        String matchId = UUID.randomUUID().toString();
        Coach coach = coach();
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);
        given(sseService.subscribe(matchId, coach)).willReturn(new SseEmitter());

        mockMvc.perform(get("/api/notifications/subscribe/{matchId}", matchId).with(roleJwt("coach")))
                .andExpect(status().isOk());
        verify(sseService).subscribe(matchId, coach);
    }

    @Test
    void subscribe_objectForbidden_returns403() throws Exception {
        String matchId = UUID.randomUUID().toString();
        Coach coach = coach();
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);
        given(sseService.subscribe(eq(matchId), eq(coach)))
                .willThrow(new ForbiddenException("error.auth.forbidden", new Object[0], "denied"));

        mockMvc.perform(get("/api/notifications/subscribe/{matchId}", matchId).with(roleJwt("coach")))
                .andExpect(status().isForbidden());
    }

    private static Coach coach() {
        Coach coach = new Coach();
        coach.setId(UUID.randomUUID());
        coach.setRole(UserRole.COACH);
        return coach;
    }

    private static RequestPostProcessor roleJwt(String role) {
        return jwt().jwt(jwt -> jwt
                        .claim("azp", "football-web-client")
                        .claim("email", role + "@test.com")
                        .claim("resource_access", java.util.Map.of(
                                "football-web-client", java.util.Map.of("roles", java.util.List.of(role)))))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role), new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
    }
}
