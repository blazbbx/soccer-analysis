package com.example.footballanalysis.controller;

import com.example.footballanalysis.service.TestHats;

import com.example.footballanalysis.model.db.user.User;

import com.example.footballanalysis.config.SecurityConfig;
import com.example.footballanalysis.model.requests.SubmitAssignmentRequest;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.service.HomeworkAnswers;
import com.example.footballanalysis.service.HomeworkAssignmentViews;
import com.example.footballanalysis.service.HomeworkComments;
import com.example.footballanalysis.service.HomeworkReview;
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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HomeworkAssignmentController.class)
@Import({SecurityConfig.class, KeycloakRoleConverter.class, HomeworkAssignmentControllerAclWebMvcTest.MockConfig.class})
class HomeworkAssignmentControllerAclWebMvcTest {

    private static final String REVIEW = "{\"feedback\":\"ok\"}";
    private static final String RETURN = "{\"note\":\"Javítsd\"}";

    @TestConfiguration
    static class MockConfig {
        @Bean HomeworkAnswers homeworkAnswers() { return Mockito.mock(HomeworkAnswers.class); }
        @Bean HomeworkAssignmentViews homeworkAssignmentViews() { return Mockito.mock(HomeworkAssignmentViews.class); }
        @Bean HomeworkComments homeworkComments() { return Mockito.mock(HomeworkComments.class); }
        @Bean HomeworkReview homeworkReview() { return Mockito.mock(HomeworkReview.class); }
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
    @Autowired HomeworkAssignmentViews views;
    @Autowired HomeworkAnswers answers;
    @Autowired HomeworkReview review;
    @Autowired HomeworkComments comments;
    @Autowired UserAccessService userAccessService;

    private final UUID id = UUID.randomUUID();
    private User parent;

    @BeforeEach
    void actor() {
        clearInvocations(views, answers, review, comments);
        parent = TestHats.user(UserRole.PARENT);
        parent.setId(UUID.randomUUID());
        TestHats.grant(parent, UserRole.PARENT);
        given(userAccessService.resolveCurrentUser(any())).willReturn(parent);
    }

    @Test
    void aFanIsStoppedOnEveryRouteBeforeTheService() throws Exception {
        List<MockHttpServletRequestBuilder> all = List.of(
                get("/api/me/homeworks"),
                get("/api/me/homeworks/summary"),
                get("/api/homework-assignments/" + id),
                post("/api/homework-assignments/" + id + "/view"),
                post("/api/homework-assignments/" + id + "/submit").contentType(APPLICATION_JSON).content("{}"),
                post("/api/homework-assignments/" + id + "/review").contentType(APPLICATION_JSON).content(REVIEW),
                post("/api/homework-assignments/" + id + "/return").contentType(APPLICATION_JSON).content(RETURN),
                get("/api/homework-assignments/" + id + "/comments"),
                post("/api/homework-assignments/" + id + "/comments").contentType(APPLICATION_JSON).content("{\"body\":\"x\"}"));
        for (MockHttpServletRequestBuilder request : all) {
            mockMvc.perform(request.with(jwtWith("fan"))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(views, answers, review, comments);
    }

    @Test
    void aCoachCannotAnswerAndARecipientCannotDecide() throws Exception {
        mockMvc.perform(post("/api/homework-assignments/{id}/submit", id).contentType(APPLICATION_JSON).content("{}").with(jwtWith("coach")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/homework-assignments/{id}/submit", id).contentType(APPLICATION_JSON).content("{}").with(jwtWith("club_admin")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/homework-assignments/{id}/view", id).with(jwtWith("coach"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/me/homeworks").with(jwtWith("coach"))).andExpect(status().isForbidden());
        for (String role : List.of("player", "parent")) {
            mockMvc.perform(post("/api/homework-assignments/{id}/review", id).contentType(APPLICATION_JSON).content(REVIEW).with(jwtWith(role)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/homework-assignments/{id}/return", id).contentType(APPLICATION_JSON).content(RETURN).with(jwtWith(role)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(views, answers, review, comments);
    }

    @Test
    void theRecipientRoutesAnswer() throws Exception {
        mockMvc.perform(post("/api/homework-assignments/{id}/view", id).with(jwtWith("parent"))).andExpect(status().isNoContent());
        verify(answers).markViewed(id, parent);
        mockMvc.perform(post("/api/homework-assignments/{id}/submit", id).contentType(APPLICATION_JSON).content("{}").with(jwtWith("parent")))
                .andExpect(status().isOk());
        verify(answers).submit(id, new SubmitAssignmentRequest(null, null, null), parent);
        // The answer comes with the hand-in, so the body is required.
        mockMvc.perform(post("/api/homework-assignments/{id}/submit", id).with(jwtWith("parent"))).andExpect(status().isBadRequest());
    }

    @Test
    void invalidBodiesNeverReachTheService() throws Exception {
        mockMvc.perform(post("/api/homework-assignments/{id}/review", id).contentType(APPLICATION_JSON)
                        .content("{\"feedback\":\"" + "x".repeat(5001) + "\"}").with(jwtWith("coach")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/homework-assignments/{id}/comments", id).contentType(APPLICATION_JSON)
                        .content("{\"body\":\" \"}").with(jwtWith("parent")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/homework-assignments/{id}/submit", id).contentType(APPLICATION_JSON)
                        .content("{\"responseText\":\"" + "x".repeat(10001) + "\"}").with(jwtWith("parent")))
                .andExpect(status().isBadRequest());
        // Sending back needs a note: the player has to know what to fix.
        mockMvc.perform(post("/api/homework-assignments/{id}/return", id).contentType(APPLICATION_JSON)
                        .content("{\"note\":\" \"}").with(jwtWith("coach")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/homework-assignments/{id}/return", id).contentType(APPLICATION_JSON)
                        .content("{\"note\":\"" + "x".repeat(2001) + "\"}").with(jwtWith("coach")))
                .andExpect(status().isBadRequest());
        verify(review, never()).review(any(), any(), any());
        verify(review, never()).returnForRevision(any(), any(), any());
        verify(comments, never()).addComment(any(), any(), any());
        verify(answers, never()).submit(any(), any(), any());
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
