package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.responses.MeViewsResponse;
import com.example.footballanalysis.service.MeService;
import com.example.footballanalysis.service.PlayerParentService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MeControllerWebMvcTest.MockConfig.class)
@DisplayName("MeController WebMvc tesztek")
class MeControllerWebMvcTest {

    @TestConfiguration
    static class MockConfig {
        @Bean
        UserAccessService userAccessService() {
            return Mockito.mock(UserAccessService.class);
        }

        @Bean
        PlayerParentService playerParentService() {
            return Mockito.mock(PlayerParentService.class);
        }

        @Bean
        MeService meService() {
            return Mockito.mock(MeService.class);
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
    MeService meService;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(meService);
    }

    @Test
    void viewsReturnsHatsAndMembershipIds() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        given(meService.getViews(any())).willReturn(new MeViewsResponse(
                UserRole.PARENT,
                List.of(UserRole.COACH, UserRole.PARENT),
                List.of(),
                List.of(teamId),
                List.of(profileId),
                List.of(),
                List.of()));

        mockMvc.perform(get("/api/me/views").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryRole").value("PARENT"))
                .andExpect(jsonPath("$.hats[0]").value("COACH"))
                .andExpect(jsonPath("$.hats[1]").value("PARENT"))
                .andExpect(jsonPath("$.coachTeamIds[0]").value(teamId.toString()))
                .andExpect(jsonPath("$.parentProfileIds[0]").value(profileId.toString()))
                .andExpect(jsonPath("$.clubAdminClubIds").isEmpty());
    }
}
