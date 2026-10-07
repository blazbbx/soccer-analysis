package com.example.footballanalysis.controller;

import com.example.footballanalysis.config.SecurityConfig;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.responses.MatchResponse;
import com.example.footballanalysis.model.responses.PageResponse;
import com.example.footballanalysis.service.ClipService;
import com.example.footballanalysis.service.MatchIngestionOrchestrator;
import com.example.footballanalysis.service.MatchService;
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
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

import java.util.Locale;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MatchController.class)
@Import({SecurityConfig.class, KeycloakRoleConverter.class, MatchControllerAclWebMvcTest.MockConfig.class})
@DisplayName("MatchController ACL (security filters on)")
class MatchControllerAclWebMvcTest {

    @TestConfiguration
    static class MockConfig {
        @Bean MatchService matchService() { return Mockito.mock(MatchService.class); }
        @Bean ClipService clipService() { return Mockito.mock(ClipService.class); }
        @Bean UserAccessService userAccessService() { return Mockito.mock(UserAccessService.class); }
        @Bean MatchIngestionOrchestrator matchIngestionOrchestrator() {
            return Mockito.mock(MatchIngestionOrchestrator.class);
        }
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
    @Autowired MatchService matchService;
    @Autowired ClipService clipService;
    @Autowired UserAccessService userAccessService;
    @Autowired MatchIngestionOrchestrator matchIngestionOrchestrator;

    @Test
    void getMatch_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/matches/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMatch_authenticatedWithoutAppRole_returns403() throws Exception {
        mockMvc.perform(get("/api/matches/{id}", UUID.randomUUID())
                        .with(jwt().jwt(jwt -> jwt
                                .claim("azp", "football-web-client")
                                .claim("email", "user@test.com"))
                                .authorities(new SimpleGrantedAuthority("ROLE_user"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMatch_coachWithoutObjectAccess_returns403() throws Exception {
        UUID matchId = UUID.randomUUID();
        Coach coach = new Coach();
        coach.setId(UUID.randomUUID());
        coach.setRole(UserRole.COACH);
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);
        given(matchService.getMatchDetails(eq(matchId), any()))
                .willThrow(new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied to match."));

        mockMvc.perform(get("/api/matches/{id}", matchId)
                        .with(coachJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void confirmCorners_passesActorWhenFetchingUpdatedMatch() throws Exception {
        UUID matchId = UUID.randomUUID();
        Coach coach = new Coach();
        coach.setId(UUID.randomUUID());
        coach.setRole(UserRole.COACH);
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);
        given(matchService.getMatchDetails(eq(matchId), eq(coach))).willReturn(
                new MatchResponse(matchId, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, "PROCESSING", null, null, null));

        mockMvc.perform(post("/api/matches/{id}/confirm-corners", matchId)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"corners":[
                                  {"x":0,"y":0},{"x":1,"y":0},{"x":1,"y":1},{"x":0,"y":1}
                                ]}
                                """)
                        .with(coachJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus").value("PROCESSING"));

        verify(matchIngestionOrchestrator).confirmCornersAndDispatch(eq(matchId), any(), eq(coach));
        verify(matchService).getMatchDetails(matchId, coach);
    }

    @Test
    void getMatch_coachWithAccess_returns200() throws Exception {
        UUID matchId = UUID.randomUUID();
        Coach coach = new Coach();
        coach.setId(UUID.randomUUID());
        coach.setRole(UserRole.COACH);
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);
        given(matchService.getMatchDetails(eq(matchId), any())).willReturn(
                new MatchResponse(matchId, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, "COMPLETED", null, null, null));

        mockMvc.perform(get("/api/matches/{id}", matchId)
                        .with(coachJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(matchId.toString()));
    }

    @Test
    void getMatchClips_coach_returns200() throws Exception {
        UUID matchId = UUID.randomUUID();
        Coach coach = new Coach();
        coach.setId(UUID.randomUUID());
        coach.setRole(UserRole.COACH);
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);
        given(clipService.getClipsForMatch(eq(matchId), eq(coach), nullable(Integer.class), nullable(Integer.class)))
                .willReturn(new PageResponse<>(java.util.List.of(), 1, 20, 0, 0));

        mockMvc.perform(get("/api/matches/{id}/clips", matchId).with(coachJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").value(0));
        verify(clipService).getClipsForMatch(matchId, coach, null, null);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor coachJwt() {
        return jwt().jwt(jwt -> jwt
                        .claim("azp", "football-web-client")
                        .claim("email", "coach@test.com")
                        .claim("resource_access", java.util.Map.of(
                                "football-web-client", java.util.Map.of("roles", java.util.List.of("coach")))))
                .authorities(new SimpleGrantedAuthority("ROLE_coach"), new SimpleGrantedAuthority("ROLE_COACH"));
    }
}
