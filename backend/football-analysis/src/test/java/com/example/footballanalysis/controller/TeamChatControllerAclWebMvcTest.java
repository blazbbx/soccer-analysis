package com.example.footballanalysis.controller;

import com.example.footballanalysis.config.SecurityConfig;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.responses.ChatHistoryResponse;
import com.example.footballanalysis.model.responses.ChatMessageResponse;
import com.example.footballanalysis.service.ChatService;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TeamChatController.class)
@Import({SecurityConfig.class, KeycloakRoleConverter.class, TeamChatControllerAclWebMvcTest.MockConfig.class})
@DisplayName("TeamChatController ACL (security filters on)")
class TeamChatControllerAclWebMvcTest {

    @TestConfiguration
    static class MockConfig {
        @Bean ChatService chatService() { return Mockito.mock(ChatService.class); }
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
    @Autowired ChatService chatService;
    @Autowired SseNotificationService sseNotificationService;
    @Autowired UserAccessService userAccessService;

    @Test
    void getHistory_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/teams/{id}/chat", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sendMessage_emptyContent_returns400() throws Exception {
        mockMvc.perform(post("/api/teams/{id}/chat", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  \"}")
                        .with(roleJwt("coach")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendMessage_coach_returns200() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Coach coach = coach();
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);
        given(chatService.sendMessage(eq(teamId), eq(coach), any()))
                .willReturn(new ChatMessageResponse(messageId, teamId, coach.getId(), "C", "C", "Coach", "hello",
                        LocalDateTime.of(2026, 1, 1, 12, 0), null, null));

        mockMvc.perform(post("/api/teams/{id}/chat", teamId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\"}")
                        .with(roleJwt("coach")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("hello"));
    }

    @Test
    void getHistory_forbiddenByService_returns403() throws Exception {
        UUID teamId = UUID.randomUUID();
        Coach coach = coach();
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);
        given(chatService.getHistory(eq(teamId), eq(coach), any(), any()))
                .willThrow(new ForbiddenException("error.auth.forbidden", new Object[0], "denied"));

        mockMvc.perform(get("/api/teams/{id}/chat", teamId).with(roleJwt("coach")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getHistory_fan_returns200() throws Exception {
        UUID teamId = UUID.randomUUID();
        Coach fan = coach();
        fan.setRole(UserRole.FAN);
        given(userAccessService.resolveCurrentUser(any())).willReturn(fan);
        given(chatService.getHistory(eq(teamId), eq(fan), any(), eq(50)))
                .willReturn(new ChatHistoryResponse(List.of(), false, null));

        mockMvc.perform(get("/api/teams/{id}/chat", teamId).with(roleJwt("fan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void subscribe_coach_returns200() throws Exception {
        UUID teamId = UUID.randomUUID();
        Coach coach = coach();
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);
        given(sseNotificationService.subscribeToTeamChat(coach, teamId)).willReturn(new SseEmitter());

        mockMvc.perform(get("/api/teams/{id}/chat/subscribe", teamId).with(roleJwt("coach")))
                .andExpect(status().isOk());
        verify(sseNotificationService).subscribeToTeamChat(coach, teamId);
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
