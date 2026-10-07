package com.example.footballanalysis.controller;

import com.example.footballanalysis.config.SecurityConfig;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.responses.CupListItem;
import com.example.footballanalysis.model.responses.CupMatchResponse;
import com.example.footballanalysis.model.responses.CupResponse;
import com.example.footballanalysis.model.responses.PageResponse;
import com.example.footballanalysis.model.responses.CupStandingsRowResponse;
import com.example.footballanalysis.model.responses.CupTeamResponse;
import com.example.footballanalysis.service.CupService;
import com.example.footballanalysis.service.CupStandingsService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CupController.class)
@Import({SecurityConfig.class, KeycloakRoleConverter.class, CupControllerAclWebMvcTest.MockConfig.class})
@DisplayName("CupController ACL (security filters on)")
class CupControllerAclWebMvcTest {

    @TestConfiguration
    static class MockConfig {
        @Bean CupService cupService() { return Mockito.mock(CupService.class); }
        @Bean CupStandingsService cupStandingsService() { return Mockito.mock(CupStandingsService.class); }
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
    @Autowired CupService cupService;
    @Autowired CupStandingsService cupStandingsService;
    @Autowired UserAccessService userAccessService;

    @Test
    void getCups_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/cups"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCups_player_returns200() throws Exception {
        Coach actor = actor(UserRole.PLAYER);
        UUID cupId = UUID.randomUUID();
        given(userAccessService.resolveCurrentUser(any())).willReturn(actor);
        given(cupService.listAllCups(eq(actor), isNull(), isNull(), isNull(), isNull())).willReturn(
                new PageResponse<>(List.of(new CupListItem(
                        cupId, "Spring Cup", LocalDateTime.of(2026, 1, 1, 0, 0), actor.getId(), "Ada", "Player", 0, 0
                )), 1, 20, 1, 1));

        mockMvc.perform(get("/api/cups").with(roleJwt("player")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Spring Cup"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void createCup_player_returns403() throws Exception {
        mockMvc.perform(post("/api/cups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Spring Cup\"}")
                        .with(roleJwt("player")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCup_coach_returns200() throws Exception {
        Coach coach = actor(UserRole.COACH);
        UUID cupId = UUID.randomUUID();
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);
        given(cupService.createCup(any(), eq(coach))).willReturn(cup(cupId, "Spring Cup"));

        mockMvc.perform(post("/api/cups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Spring Cup\"}")
                        .with(roleJwt("coach")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cupId.toString()));
    }

    @Test
    void getCup_objectForbidden_returns403() throws Exception {
        UUID cupId = UUID.randomUUID();
        Coach coach = actor(UserRole.COACH);
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);
        given(cupService.getCup(eq(cupId), eq(coach), isNull()))
                .willThrow(new ForbiddenException("error.auth.forbidden", new Object[0], "denied"));

        mockMvc.perform(get("/api/cups/{id}", cupId).with(roleJwt("coach")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getStandings_player_returns200() throws Exception {
        UUID cupId = UUID.randomUUID();
        Coach player = actor(UserRole.PLAYER);
        given(userAccessService.resolveCurrentUser(any())).willReturn(player);
        given(cupStandingsService.calculateStandings(cupId)).willReturn(List.of(
                new CupStandingsRowResponse(UUID.randomUUID(), "Team A", 1, 1, 0, 0, 2, 0, 2, 3)));

        mockMvc.perform(get("/api/cups/{id}/standings", cupId).with(roleJwt("player")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teamName").value("Team A"));
        verify(cupService).requireCupViewer(eq(cupId), eq(player), isNull());
    }

    @Test
    void addTeam_player_returns403() throws Exception {
        mockMvc.perform(post("/api/cups/{id}/teams", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"U19\"}")
                        .with(roleJwt("player")))
                .andExpect(status().isForbidden());
    }

    @Test
    void addTeam_coach_returns200() throws Exception {
        UUID cupId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Coach coach = actor(UserRole.COACH);
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);
        given(cupService.addTeamToCup(eq(cupId), any(), eq(coach)))
                .willReturn(new CupTeamResponse(teamId, "U19", null, LocalDateTime.of(2026, 1, 1, 0, 0)));

        mockMvc.perform(post("/api/cups/{id}/teams", cupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"U19\"}")
                        .with(roleJwt("coach")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("U19"));
    }

    @Test
    void updateAndDeleteTeam_coach_ok() throws Exception {
        UUID cupId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Coach coach = actor(UserRole.COACH);
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);
        given(cupService.updateTeam(eq(cupId), eq(teamId), any(), eq(coach)))
                .willReturn(new CupTeamResponse(teamId, "U21", null, LocalDateTime.of(2026, 1, 1, 0, 0)));

        mockMvc.perform(put("/api/cups/{cupId}/teams/{teamId}", cupId, teamId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"U21\"}")
                        .with(roleJwt("coach")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("U21"));

        mockMvc.perform(delete("/api/cups/{cupId}/teams/{teamId}", cupId, teamId).with(roleJwt("coach")))
                .andExpect(status().isNoContent());
        verify(cupService).deleteTeam(cupId, teamId, coach);
    }

    @Test
    void scheduleUpdateDeleteMatch_coach_ok() throws Exception {
        UUID cupId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID home = UUID.randomUUID();
        UUID away = UUID.randomUUID();
        Coach coach = actor(UserRole.COACH);
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);
        CupMatchResponse scheduled = new CupMatchResponse(
                matchId, null, null, null, null, false,
                LocalDateTime.of(2099, 6, 1, 18, 0), null, LocalDateTime.of(2026, 1, 1, 0, 0));
        given(cupService.scheduleMatch(eq(cupId), any(), eq(coach))).willReturn(scheduled);
        given(cupService.updateMatch(eq(cupId), eq(matchId), any(), eq(coach))).willReturn(scheduled);

        mockMvc.perform(post("/api/cups/{id}/matches", cupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeTeamId\":\"" + home + "\",\"awayTeamId\":\"" + away
                                + "\",\"scheduledAt\":\"2099-06-01T18:00:00\"}")
                        .with(roleJwt("coach")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(matchId.toString()));

        mockMvc.perform(put("/api/cups/{cupId}/matches/{matchId}", cupId, matchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeTeamId\":\"" + home + "\",\"awayTeamId\":\"" + away
                                + "\",\"scheduledAt\":\"2099-06-01T18:00:00\"}")
                        .with(roleJwt("coach")))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/cups/{cupId}/matches/{matchId}", cupId, matchId).with(roleJwt("coach")))
                .andExpect(status().isNoContent());
        verify(cupService).deleteMatch(cupId, matchId, coach);
    }

    @Test
    void recordScoreAndLinkRealMatch_coach_ok() throws Exception {
        UUID cupId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID realMatchId = UUID.randomUUID();
        Coach coach = actor(UserRole.COACH);
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);
        CupMatchResponse scored = new CupMatchResponse(
                matchId, null, null, 2, 1, true,
                LocalDateTime.of(2099, 6, 1, 18, 0), realMatchId, LocalDateTime.of(2026, 1, 1, 0, 0));
        given(cupService.recordScore(eq(cupId), eq(matchId), any(), eq(coach))).willReturn(scored);
        given(cupService.linkRealMatch(eq(cupId), eq(matchId), eq(realMatchId), eq(coach))).willReturn(scored);
        given(cupService.unlinkRealMatch(eq(cupId), eq(matchId), eq(coach))).willReturn(scored);

        mockMvc.perform(put("/api/cups/{cupId}/matches/{matchId}/score", cupId, matchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeScore\":2,\"awayScore\":1}")
                        .with(roleJwt("coach")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeScore").value(2));

        mockMvc.perform(put("/api/cups/{cupId}/matches/{matchId}/real-match", cupId, matchId)
                        .param("realMatchId", realMatchId.toString())
                        .with(roleJwt("coach")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realMatchId").value(realMatchId.toString()));

        mockMvc.perform(delete("/api/cups/{cupId}/matches/{matchId}/real-match", cupId, matchId)
                        .with(roleJwt("coach")))
                .andExpect(status().isOk());
    }

    @Test
    void getCupTeams_player_returns200() throws Exception {
        UUID cupId = UUID.randomUUID();
        Coach player = actor(UserRole.PLAYER);
        given(userAccessService.resolveCurrentUser(any())).willReturn(player);
        given(cupService.getCup(eq(cupId), eq(player), isNull())).willReturn(cup(cupId, "Spring Cup"));

        mockMvc.perform(get("/api/cups/{id}/teams", cupId).with(roleJwt("player")))
                .andExpect(status().isOk());
    }

    @Test
    void getCupTeamsAndMatches_coach_returns200() throws Exception {
        UUID cupId = UUID.randomUUID();
        Coach coach = actor(UserRole.COACH);
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);
        given(cupService.getCup(eq(cupId), eq(coach), isNull())).willReturn(cup(cupId, "Spring Cup"));

        mockMvc.perform(get("/api/cups/{id}/teams", cupId).with(roleJwt("coach")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/cups/{id}/matches", cupId).with(roleJwt("coach")))
                .andExpect(status().isOk());
    }

    private static Coach actor(UserRole role) {
        Coach user = new Coach();
        user.setId(UUID.randomUUID());
        user.setRole(role);
        return user;
    }

    private static CupResponse cup(UUID id, String name) {
        return new CupResponse(id, name, LocalDateTime.of(2026, 1, 1, 0, 0), null, Set.of(), Set.of());
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
