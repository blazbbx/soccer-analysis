package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.responses.ClubResponse;
import com.example.footballanalysis.service.ClubAdminInviteService;
import com.example.footballanalysis.service.ClubService;
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

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ClubController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ClubControllerWebMvcTest.MockConfig.class)
@DisplayName("ClubController WebMvc tests")
class ClubControllerWebMvcTest {

    @TestConfiguration
    static class MockConfig {
        @Bean
        ClubService clubService() {
            return Mockito.mock(ClubService.class);
        }

        @Bean
        ClubAdminInviteService clubAdminInviteService() {
            return Mockito.mock(ClubAdminInviteService.class);
        }

        @Bean
        LocaleResolver localeResolver() {
            return new FixedLocaleResolver(Locale.ENGLISH);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ClubService clubService;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(clubService);
    }

    @Test
    void listClubs_returnsPayload() throws Exception {
        UUID clubId = UUID.randomUUID();
        given(clubService.listClubs(any())).willReturn(List.of(
                new ClubResponse(clubId, "Vasas", "VAS", null, Instant.parse("2026-01-01T00:00:00Z"), false)
        ));

        mockMvc.perform(get("/api/clubs").with(jwt().jwt(this::jwtForCoach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(clubId.toString()))
                .andExpect(jsonPath("$[0].name").value("Vasas"));
    }

    @Test
    void createClub_returnsCreatedClub() throws Exception {
        UUID clubId = UUID.randomUUID();
        given(clubService.createClub(any(), any())).willReturn(
                new ClubResponse(clubId, "Fradi", "FTC", null, Instant.parse("2026-01-01T00:00:00Z"), false));

        mockMvc.perform(post("/api/clubs")
                        .with(jwt().jwt(this::jwtForCoach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fradi\",\"shortName\":\"FTC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Fradi"));
    }

    @Test
    void deleteClub_returnsNoContent() throws Exception {
        UUID clubId = UUID.randomUUID();
        mockMvc.perform(delete("/api/clubs/" + clubId).with(jwt().jwt(this::jwtForCoach)))
                .andExpect(status().isNoContent());
    }

    @Test
    void updateClub_returnsPayload() throws Exception {
        UUID clubId = UUID.randomUUID();
        given(clubService.updateClub(any(), any(), any())).willReturn(
                new ClubResponse(clubId, "U11 Club", "U11", null, Instant.parse("2026-01-01T00:00:00Z"), false));

        mockMvc.perform(put("/api/clubs/" + clubId)
                        .with(jwt().jwt(this::jwtForCoach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"U11 Club\",\"shortName\":\"U11\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("U11 Club"));
    }

    private void jwtForCoach(Jwt.Builder jwt) {
        jwt.claim("email", "coach@test.com");
    }
}
