package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.responses.TeamResponse;
import com.example.footballanalysis.service.TeamService;
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
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @BeforeEach
    void resetMocks() {
        Mockito.reset(teamService);
    }

    @Test
    void getMyTeams_returnsTeamsForAuthenticatedUser() throws Exception {
        UUID teamId = UUID.randomUUID();
        given(teamService.getMyTeams(any())).willReturn(List.of(
                new TeamResponse(teamId, "Arsenal", "ARS", null, List.of(), List.of())
        ));

        mockMvc.perform(get("/api/teams/me")
                        .with(jwt().jwt(this::jwtForCoach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(teamId.toString()))
                .andExpect(jsonPath("$[0].name").value("Arsenal"))
                .andExpect(jsonPath("$[0].shortName").value("ARS"));
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


