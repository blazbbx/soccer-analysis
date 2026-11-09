package com.example.footballanalysis.controller;

import com.example.footballanalysis.config.SecurityConfig;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.model.db.HomeworkStatus;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.responses.HomeworkDetailResponse;
import com.example.footballanalysis.service.HomeworkContent;
import com.example.footballanalysis.service.HomeworkRecipients;
import com.example.footballanalysis.service.HomeworkService;
import com.example.footballanalysis.service.UserAccessService;
import com.example.footballanalysis.service.UserRoleSyncService;
import com.example.footballanalysis.util.KeycloakRoleConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HomeworkController.class)
@Import({SecurityConfig.class, KeycloakRoleConverter.class, HomeworkControllerAclWebMvcTest.MockConfig.class})
class HomeworkControllerAclWebMvcTest {

    private static final String VALID_BODY = """
            {"title":"Lecke","items":[],"entireRoster":false}
            """;

    @TestConfiguration
    static class MockConfig {
        @Bean HomeworkService homeworkService() { return Mockito.mock(HomeworkService.class); }
        @Bean HomeworkRecipients homeworkRecipients() { return Mockito.mock(HomeworkRecipients.class); }
        @Bean HomeworkContent homeworkContent() { return Mockito.mock(HomeworkContent.class); }
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
    @Autowired HomeworkService homeworkService;
    @Autowired HomeworkRecipients homeworkRecipients;
    @Autowired HomeworkContent homeworkContent;
    @Autowired UserAccessService userAccessService;

    private final UUID teamId = UUID.randomUUID();
    private final UUID homeworkId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();
    private Coach coach;

    @BeforeEach
    void actor() {
        clearInvocations(homeworkService, homeworkRecipients, homeworkContent);
        coach = new Coach();
        coach.setId(UUID.randomUUID());
        coach.setRole(UserRole.COACH);
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);
    }

    @Test
    void fansPlayersAndParentsAreStoppedOnEveryRouteBeforeTheService() throws Exception {
        for (String role : List.of("fan", "player", "parent")) {
            for (MockHttpServletRequestBuilder request : allRoutes()) {
                mockMvc.perform(request.with(jwtWith(role))).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(homeworkService, homeworkRecipients, homeworkContent);
    }

    @Test
    void adminClubAdminAndCoachReachTheService() throws Exception {
        given(homeworkService.get(eq(teamId), eq(homeworkId), any())).willReturn(detail());
        for (String role : List.of("admin", "club_admin", "coach")) {
            mockMvc.perform(get("/api/teams/{teamId}/homeworks/{id}", teamId, homeworkId).with(jwtWith(role)))
                    .andExpect(status().isOk());
        }
        verify(homeworkService, Mockito.times(3)).get(eq(teamId), eq(homeworkId), eq(coach));
    }

    @Test
    void theRoutesAnswerWithThePlannedStatus() throws Exception {
        given(homeworkService.create(eq(teamId), any(), eq(coach))).willReturn(detail());
        given(homeworkService.get(homeworkId, coach)).willReturn(detail());

        mockMvc.perform(post("/api/teams/{teamId}/homeworks", teamId).contentType(APPLICATION_JSON).content(VALID_BODY).with(jwtWith("coach")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        mockMvc.perform(get("/api/homeworks/{id}", homeworkId).with(jwtWith("coach")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(teamId.toString()));
        mockMvc.perform(delete("/api/teams/{teamId}/homeworks/{id}", teamId, homeworkId).with(jwtWith("coach")))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/teams/{teamId}/homeworks/{id}/assignees/{profileId}", teamId, homeworkId, profileId).with(jwtWith("coach")))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/teams/{teamId}/homeworks/{id}/duplicate", teamId, homeworkId).with(jwtWith("coach")))
                .andExpect(status().isOk());
        verify(homeworkService).duplicate(teamId, homeworkId, coach);
    }

    @Test
    void anEmptyTitleOrTooManyItemsNeverReachTheService() throws Exception {
        mockMvc.perform(post("/api/teams/{teamId}/homeworks", teamId).contentType(APPLICATION_JSON)
                        .content("""
                                {"title":" ","items":[]}
                                """)
                        .with(jwtWith("coach")))
                .andExpect(status().isBadRequest());

        String thirtyOne = "{\"type\":\"TEXT\",\"body\":\"x\"}" + ",{\"type\":\"TEXT\",\"body\":\"x\"}".repeat(30);
        mockMvc.perform(post("/api/teams/{teamId}/homeworks", teamId).contentType(APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"items\":[" + thirtyOne + "]}")
                        .with(jwtWith("coach")))
                .andExpect(status().isBadRequest());
        verify(homeworkService, never()).create(any(), any(), any());
    }

    @Test
    void aConflictBecomesA409WithItsTranslatedMessage() throws Exception {
        given(homeworkService.archive(teamId, homeworkId, coach)).willThrow(new ConflictException(
                "error.homework.invalid_transition", new Object[0], "fallback"));

        mockMvc.perform(post("/api/teams/{teamId}/homeworks/{id}/archive", teamId, homeworkId).with(jwtWith("coach")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This action is not possible in the current state."));
    }

    private List<MockHttpServletRequestBuilder> allRoutes() {
        String base = "/api/teams/" + teamId + "/homeworks";
        String one = base + "/" + homeworkId;
        return List.of(
                get(base),
                post(base).contentType(APPLICATION_JSON).content(VALID_BODY),
                get(one),
                put(one).contentType(APPLICATION_JSON).content(VALID_BODY),
                delete(one),
                post(one + "/publish").contentType(APPLICATION_JSON).content("{\"entireRoster\":true}"),
                post(one + "/archive"),
                post(one + "/duplicate"),
                post(one + "/assignees").contentType(APPLICATION_JSON).content("{\"playerProfileIds\":[]}"),
                delete(one + "/assignees/" + profileId),
                get(one + "/assignments"),
                get("/api/homeworks/" + homeworkId));
    }

    private HomeworkDetailResponse detail() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 10, 0);
        return new HomeworkDetailResponse(homeworkId, teamId, "Lecke", null, HomeworkStatus.DRAFT, null, true, now, now, false, List.of(), List.of(), null);
    }

    private static RequestPostProcessor jwtWith(String... roles) {
        return jwt().jwt(token -> token
                        .claim("azp", "football-web-client")
                        .claim("email", roles[0] + "@test.com")
                        .claim("resource_access", Map.of(
                                "football-web-client", Map.of("roles", List.of(roles)))))
                .authorities(java.util.Arrays.stream(roles)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}
