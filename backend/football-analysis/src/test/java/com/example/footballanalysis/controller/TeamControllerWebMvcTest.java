package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.requests.CreatePlayerProfileRequest;
import com.example.footballanalysis.model.responses.TeamCatalogItem;
import com.example.footballanalysis.model.responses.TeamCatalogResponse;
import com.example.footballanalysis.model.responses.TeamResponse;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.service.TeamService;
import com.example.footballanalysis.service.UserAccessService;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TeamController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(TeamControllerWebMvcTest.MockConfig.class)
@DisplayName("TeamController WebMvc tesztek")
class TeamControllerWebMvcTest {

    @TestConfiguration
    static class MockConfig {
        @Bean
        TeamService teamService() {
            return Mockito.mock(TeamService.class);
        }

        @Bean
        com.example.footballanalysis.service.UserAccessService userAccessService() {
            return Mockito.mock(com.example.footballanalysis.service.UserAccessService.class);
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
    TeamService teamService;

    @Autowired
    UserAccessService userAccessService;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(teamService);
    }

    @Test
    void getMyTeams_returnsTeamsForAuthenticatedUser() throws Exception {
        UUID teamId = UUID.randomUUID();
        given(teamService.getMyTeams(any(), eq(UserRole.COACH))).willReturn(List.of(
                new TeamResponse(teamId, "Arsenal", "ARS", null, null, null, List.of(), List.of(), List.of(), List.of())
        ));

        mockMvc.perform(get("/api/teams/me").param("role", "COACH")
                        .with(jwt().jwt(this::jwtForCoach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(teamId.toString()))
                .andExpect(jsonPath("$[0].name").value("Arsenal"))
                .andExpect(jsonPath("$[0].shortName").value("ARS"));
    }

    @Test
    void listCatalog_passesRoleAndQuery() throws Exception {
        UUID teamId = UUID.randomUUID();
        given(teamService.listCatalog(any(), eq(UserRole.ADMIN), eq("u19"), eq(2), eq(20))).willReturn(new TeamCatalogResponse(
                List.of(new TeamCatalogItem(teamId, "U19", "U19", null, null, "Academy", 4, 1)),
                41,
                true,
                2,
                20,
                3
        ));

        mockMvc.perform(get("/api/teams/catalog").param("role", "ADMIN").param("q", "u19")
                        .param("page", "2").param("size", "20")
                        .with(jwt().jwt(this::jwtForCoach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(41))
                .andExpect(jsonPath("$.partial").value(true))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items[0].id").value(teamId.toString()))
                .andExpect(jsonPath("$.items[0].name").value("U19"))
                .andExpect(jsonPath("$.items[0].clubName").value("Academy"))
                .andExpect(jsonPath("$.items[0].playerCount").value(4))
                .andExpect(jsonPath("$.items[0].coachCount").value(1));
    }

    @Test
    void createPlayerProfile_delegatesToService() throws Exception {
        UUID teamId = UUID.randomUUID();

        mockMvc.perform(post("/api/teams/{teamId}/player-profiles", teamId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"firstName\":\"Ann\",\"lastName\":\"Adams\"}")
                        .with(jwt().jwt(this::jwtForCoach)))
                .andExpect(status().isNoContent());

        verify(teamService).createPlayerProfile(eq(teamId), any(CreatePlayerProfileRequest.class), any());
    }

    @Test
    void removePlayer_delegatesProfileId() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        mockMvc.perform(delete("/api/teams/{teamId}/players/{profileId}", teamId, profileId)
                        .with(jwt().jwt(this::jwtForCoach)))
                .andExpect(status().isNoContent());

        verify(teamService).removePlayerFromTeam(eq(teamId), eq(profileId), any());
    }

    private void jwtForCoach(Jwt.Builder jwt) {
        jwt.claim("email", "coach@test.com")
                .claim("preferred_username", "coach@test.com")
                .claim("realm_access", java.util.Map.of("roles", java.util.List.of("coach")))
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
    }
}


