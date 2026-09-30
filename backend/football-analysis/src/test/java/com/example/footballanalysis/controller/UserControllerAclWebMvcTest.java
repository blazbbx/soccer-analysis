package com.example.footballanalysis.controller;

import com.example.footballanalysis.config.SecurityConfig;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.UpdateUserRequest;
import com.example.footballanalysis.model.responses.UserResponse;
import com.example.footballanalysis.service.UserRegistrationService;
import com.example.footballanalysis.service.UserRoleSyncService;
import com.example.footballanalysis.service.UserService;
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
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, KeycloakRoleConverter.class, UserControllerAclWebMvcTest.MockConfig.class})
@DisplayName("UserController ACL (security filters on)")
class UserControllerAclWebMvcTest {

    @TestConfiguration
    static class MockConfig {
        @Bean UserService userService() { return Mockito.mock(UserService.class); }
        @Bean UserRegistrationService userRegistrationService() {
            return Mockito.mock(UserRegistrationService.class);
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
    @Autowired UserService userService;

    @Test
    void putMe_allowedForCoach() throws Exception {
        UUID userId = UUID.randomUUID();
        given(userService.updateMyUser(any(UpdateUserRequest.class), any())).willReturn(
                new UserResponse(userId, "coach@test.com", "C", "Oach", UserRole.COACH,
                        LocalDateTime.of(2026, 1, 1, 0, 0), null));

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"C\"}")
                        .with(jwt().jwt(jwt -> jwt
                                        .claim("azp", "football-web-client")
                                        .claim("email", "coach@test.com")
                                        .claim("resource_access", java.util.Map.of(
                                                "football-web-client",
                                                java.util.Map.of("roles", java.util.List.of("coach")))))
                                .authorities(new SimpleGrantedAuthority("ROLE_coach"))))
                .andExpect(status().isOk());
    }

    @Test
    void putMe_allowedForClubAdmin() throws Exception {
        UUID userId = UUID.randomUUID();
        given(userService.updateMyUser(any(UpdateUserRequest.class), any())).willReturn(
                new UserResponse(userId, "clubadmin@test.test", "Club", "Admin", UserRole.CLUB_ADMIN,
                        LocalDateTime.of(2026, 1, 1, 0, 0), null));

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Club\"}")
                        .with(jwt().jwt(jwt -> jwt
                                        .claim("azp", "football-web-client")
                                        .claim("email", "clubadmin@test.test")
                                        .claim("resource_access", java.util.Map.of(
                                                "football-web-client",
                                                java.util.Map.of("roles", java.util.List.of("club_admin")))))
                                .authorities(new SimpleGrantedAuthority("ROLE_club_admin"))))
                .andExpect(status().isOk());
    }

    @Test
    void putOtherUser_forbiddenForCoach() throws Exception {
        mockMvc.perform(put("/api/users/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"C\"}")
                        .with(jwt().jwt(jwt -> jwt
                                        .claim("azp", "football-web-client")
                                        .claim("email", "coach@test.com"))
                                .authorities(new SimpleGrantedAuthority("ROLE_coach"))))
                .andExpect(status().isForbidden());
    }
}
