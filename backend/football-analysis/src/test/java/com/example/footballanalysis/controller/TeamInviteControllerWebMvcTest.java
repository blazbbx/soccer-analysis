package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.responses.InviteLinkResponse;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.responses.TeamInviteResponse;
import com.example.footballanalysis.service.TeamInviteService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

import java.util.Locale;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TeamInviteController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(TeamInviteControllerWebMvcTest.MockConfig.class)
@DisplayName("TeamInviteController WebMvc tesztek")
class TeamInviteControllerWebMvcTest {

    @TestConfiguration
    static class MockConfig {
        @Bean
        TeamInviteService teamInviteService() {
            return Mockito.mock(TeamInviteService.class);
        }

        @Bean
        LocaleResolver localeResolver() {
            return new FixedLocaleResolver(Locale.ENGLISH);
        }
    }

    @BeforeAll
    static void forceEnglishLocale() {
        Locale.setDefault(Locale.ENGLISH);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TeamInviteService teamInviteService;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(teamInviteService);
    }

    @Test
    void createInvite_returnsCreatedResponse() throws Exception {
        UUID teamId = UUID.randomUUID();

        given(teamInviteService.generateInviteLink(any(UUID.class), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .willReturn(new InviteLinkResponse("http://localhost:8080/api/team-invites/invite-token"));

        mockMvc.perform(post("/api/v1/teams/{teamId}/invites", teamId)
                .with(jwt().jwt(jwt -> jwt
                    .claim("email", "coach@test.com")
                    .claim("preferred_username", "coach@test.com")
                    .claim("realm_access", java.util.Map.of("roles", java.util.List.of("coach")))))
                .param("role", "PLAYER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inviteLink").value("http://localhost:8080/api/team-invites/invite-token"));
    }

    @Test
    void getInvite_returnsInviteDetails() throws Exception {
        given(teamInviteService.getInvite("invite-token")).willReturn(
                new TeamInviteResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Arsenal",
                        UserRole.PLAYER,
                        "invite-token",
                        "http://localhost:8080/api/team-invites/invite-token",
                        30,
                        2,
                        28,
                        java.time.LocalDateTime.now().plusHours(1),
                        false
                )
        );

        mockMvc.perform(get("/api/team-invites/{token}", "invite-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedCount").value(2))
                .andExpect(jsonPath("$.remainingUses").value(28));
    }

    @Test
    void acceptInvite_returnsAcceptedResponse() throws Exception {
        given(teamInviteService.acceptInvite(org.mockito.ArgumentMatchers.eq("invite-token"), org.mockito.ArgumentMatchers.any())).willReturn(
                new TeamInviteResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Arsenal",
                        UserRole.PLAYER,
                        "invite-token",
                        "http://localhost:8080/api/team-invites/invite-token",
                        30,
                        1,
                        29,
                        java.time.LocalDateTime.now().plusHours(1),
                        false
                )
        );

            mockMvc.perform(
                    post("/api/team-invites/{token}/accept", "invite-token")
                        .with(jwt().jwt(jwt -> jwt
                            .claim("email", "player@test.com")
                            .claim("preferred_username", "player@test.com")
                            .claim("realm_access", java.util.Map.of("roles", java.util.List.of("player")))))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedCount").value(1))
                .andExpect(jsonPath("$.remainingUses").value(29));
    }
}