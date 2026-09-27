package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.CreateChildLoginRequest;
import com.example.footballanalysis.model.responses.InviteTokenResponse;
import com.example.footballanalysis.model.responses.UserResponse;
import com.example.footballanalysis.service.ParentInviteService;
import com.example.footballanalysis.service.PlayerParentService;
import com.example.footballanalysis.service.TeamInviteService;
import com.example.footballanalysis.service.UserAccessService;
import com.example.footballanalysis.service.UserRegistrationService;
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

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PlayerProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PlayerProfileControllerWebMvcTest.MockConfig.class)
@DisplayName("PlayerProfileController WebMvc tesztek")
class PlayerProfileControllerWebMvcTest {

    @TestConfiguration
    static class MockConfig {
        @Bean
        TeamInviteService teamInviteService() {
            return Mockito.mock(TeamInviteService.class);
        }

        @Bean
        UserRegistrationService userRegistrationService() {
            return Mockito.mock(UserRegistrationService.class);
        }

        @Bean
        UserAccessService userAccessService() {
            return Mockito.mock(UserAccessService.class);
        }

        @Bean
        PlayerParentService playerParentService() {
            return Mockito.mock(PlayerParentService.class);
        }

        @Bean
        ParentInviteService parentInviteService() {
            return Mockito.mock(ParentInviteService.class);
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

    @Autowired
    UserRegistrationService userRegistrationService;

    @Autowired
    UserAccessService userAccessService;

    @Autowired
    PlayerParentService playerParentService;

    @Autowired
    ParentInviteService parentInviteService;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(teamInviteService, userRegistrationService, userAccessService, playerParentService, parentInviteService);
        Parent parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setRole(UserRole.PARENT);
        given(userAccessService.resolveCurrentUser(any(Jwt.class))).willReturn(parent);
    }

    @Test
    void createChildLoginInvite_returnsToken() throws Exception {
        UUID profileId = UUID.randomUUID();
        given(teamInviteService.createChildLoginInvite(eq(profileId), any()))
                .willReturn(new InviteTokenResponse("child-token"));

        mockMvc.perform(post("/api/player-profiles/{id}/child-login-invites", profileId)
                        .with(jwt().jwt(jwt -> jwt.claim("email", "parent@test.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("child-token"));
    }

    @Test
    void createChildLogin_returnsCreatedUser() throws Exception {
        UUID profileId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        given(userRegistrationService.createChildLogin(eq(profileId), any(CreateChildLoginRequest.class), any()))
                .willReturn(new UserResponse(
                        childId,
                        "peti@test.test",
                        "Peti",
                        "Teszt",
                        UserRole.PLAYER,
                        LocalDateTime.of(2026, 3, 22, 10, 0),
                        null
                ));

        mockMvc.perform(post("/api/player-profiles/{id}/child-login", profileId)
                        .with(jwt().jwt(jwt -> jwt.claim("email", "parent@test.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "peti@test.test",
                                  "password": "secret123",
                                  "firstName": "Peti",
                                  "lastName": "Teszt"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(childId.toString()))
                .andExpect(jsonPath("$.email").value("peti@test.test"))
                .andExpect(jsonPath("$.role").value("PLAYER"));
    }

    @Test
    void createChildLogin_blankEmail_returns400() throws Exception {
        UUID profileId = UUID.randomUUID();

        mockMvc.perform(post("/api/player-profiles/{id}/child-login", profileId)
                        .with(jwt().jwt(jwt -> jwt.claim("email", "parent@test.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createParentInvite_returnsToken() throws Exception {
        UUID profileId = UUID.randomUUID();
        given(parentInviteService.create(eq(profileId), any())).willReturn(new InviteTokenResponse("parent-token"));

        mockMvc.perform(post("/api/player-profiles/{id}/parent-invites", profileId)
                        .with(jwt().jwt(jwt -> jwt.claim("email", "parent@test.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("parent-token"));
    }

    @Test
    void unlinkParent_returnsNoContent() throws Exception {
        UUID profileId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();

        mockMvc.perform(delete("/api/player-profiles/{id}/parents/{parentId}", profileId, parentId)
                        .with(jwt().jwt(jwt -> jwt.claim("email", "parent@test.test"))))
                .andExpect(status().isNoContent());

        Mockito.verify(playerParentService).unlinkParent(eq(profileId), eq(parentId), any());
    }

    @Test
    void detachChildLogin_returnsNoContent() throws Exception {
        UUID profileId = UUID.randomUUID();

        mockMvc.perform(delete("/api/player-profiles/{id}/child-login", profileId)
                        .with(jwt().jwt(jwt -> jwt.claim("email", "parent@test.test"))))
                .andExpect(status().isNoContent());

        Mockito.verify(playerParentService).detachChildLogin(eq(profileId), any());
    }
}
