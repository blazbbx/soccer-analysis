package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.RegisterUserRequest;
import com.example.footballanalysis.model.requests.UpdateUserRequest;
import com.example.footballanalysis.model.responses.UserResponse;
import com.example.footballanalysis.service.UserRegistrationService;
import com.example.footballanalysis.service.UserService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.nullable;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(UserControllerWebMvcTest.MockConfig.class)
@DisplayName("UserController WebMvc tesztek")
class UserControllerWebMvcTest {

    @TestConfiguration
    static class MockConfig {
        @Bean
        UserService userService() {
            return Mockito.mock(UserService.class);
        }

        @Bean
        UserRegistrationService userRegistrationService() {
            return Mockito.mock(UserRegistrationService.class);
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
    UserService userService;

    @Autowired
    UserRegistrationService userRegistrationService;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(userService, userRegistrationService);
    }

    @Test
    void register_returnsCreatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        given(userRegistrationService.registerWithInvite(any(RegisterUserRequest.class))).willReturn(
                new UserResponse(
                        userId,
                        "player@test.com",
                        "Peter",
                        "Parker",
                        UserRole.PLAYER,
                        LocalDateTime.of(2026, 3, 22, 10, 0),
                        null
                )
        );

        String body = """
                {
                  "email": "player@test.com",
                  "firstName": "Peter",
                  "lastName": "Parker",
                  "password": "secret123",
                  "inviteToken": "invite-token"
                }
                """;

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("player@test.com"))
                .andExpect(jsonPath("$.role").value("PLAYER"));
    }

            @Test
            void updateMyProfile_returnsUpdatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        given(userService.updateMyUser(any(UpdateUserRequest.class), nullable(org.springframework.security.oauth2.jwt.Jwt.class))).willReturn(
                new UserResponse(
                        userId,
                        "player@test.com",
                        "Peter",
                        "Parker",
                        UserRole.PLAYER,
                        LocalDateTime.of(2026, 3, 22, 10, 0),
                        null
                )
        );

        String body = """
                {
                  "firstName": "Peter",
                  "lastName": "Parker"
                }
                """;

        mockMvc.perform(put("/api/users/me")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.firstName").value("Peter"))
                .andExpect(jsonPath("$.lastName").value("Parker"));
    }

}


