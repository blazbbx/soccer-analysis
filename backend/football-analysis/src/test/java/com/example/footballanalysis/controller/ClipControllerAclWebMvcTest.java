package com.example.footballanalysis.controller;

import com.example.footballanalysis.config.SecurityConfig;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.responses.ClipCompositionUploadResponse;
import com.example.footballanalysis.model.responses.ClipResponse;
import com.example.footballanalysis.service.ClipService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ClipController.class)
@Import({SecurityConfig.class, KeycloakRoleConverter.class, ClipControllerAclWebMvcTest.MockConfig.class})
@DisplayName("ClipController ACL (security filters on)")
class ClipControllerAclWebMvcTest {

    @TestConfiguration
    static class MockConfig {
        @Bean ClipService clipService() { return Mockito.mock(ClipService.class); }
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
    @Autowired ClipService clipService;
    @Autowired UserAccessService userAccessService;

    @Test
    void getClip_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/clips/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getClip_fan_returns403() throws Exception {
        mockMvc.perform(get("/api/clips/{id}", UUID.randomUUID()).with(roleJwt("fan")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getClip_coachWithAccess_returns200() throws Exception {
        UUID clipId = UUID.randomUUID();
        Coach coach = coach();
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);
        given(clipService.getClipById(eq(clipId), eq(coach))).willReturn(clipResponse(clipId));

        mockMvc.perform(get("/api/clips/{id}", clipId).with(roleJwt("coach")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clipId.toString()));
    }

    @Test
    void downloadUrl_player_returnsPresignedLink() throws Exception {
        UUID clipId = UUID.randomUUID();
        Coach playerLike = coach();
        given(userAccessService.resolveCurrentUser(any())).willReturn(playerLike);
        given(clipService.generateRenderedClipDownloadUrl(eq(clipId), eq(playerLike)))
                .willReturn("https://minio/presigned");

        mockMvc.perform(get("/api/clips/{id}/access", clipId).with(roleJwt("player")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").value("https://minio/presigned"));
    }

    @Test
    void downloadUrl_objectForbidden_returns403() throws Exception {
        UUID clipId = UUID.randomUUID();
        Coach coach = coach();
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);
        given(clipService.generateRenderedClipDownloadUrl(eq(clipId), eq(coach)))
                .willThrow(new ForbiddenException("error.auth.forbidden", new Object[0], "denied"));

        mockMvc.perform(get("/api/clips/{id}/access", clipId).with(roleJwt("coach")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createClip_player_returns403() throws Exception {
        mockMvc.perform(post("/api/clips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matchId\":\"" + UUID.randomUUID() + "\",\"name\":\"cut\"}")
                        .with(roleJwt("player")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createClip_coach_returns200() throws Exception {
        UUID clipId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        Coach coach = coach();
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);
        given(clipService.createClip(eq(matchId), any(), eq(coach))).willReturn(clipResponse(clipId));

        mockMvc.perform(post("/api/clips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matchId\":\"" + matchId + "\",\"name\":\"cut\"}")
                        .with(roleJwt("coach")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clipId.toString()));
    }

    @Test
    void completeComposition_coach_returns200() throws Exception {
        UUID clipId = UUID.randomUUID();
        Coach coach = coach();
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);
        given(clipService.completeCompositionUpload(eq(clipId), eq(coach))).willReturn(clipResponse(clipId));

        mockMvc.perform(post("/api/clips/{id}/composition/complete", clipId).with(roleJwt("coach")))
                .andExpect(status().isOk());
        verify(clipService).completeCompositionUpload(clipId, coach);
    }

    @Test
    void createWithUploadLinks_coach_returnsPresigns() throws Exception {
        UUID matchId = UUID.randomUUID();
        UUID clipId = UUID.randomUUID();
        Coach coach = coach();
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);
        given(clipService.createClipWithUploadLinks(eq(matchId), any(), eq(coach)))
                .willReturn(new ClipCompositionUploadResponse(clipId, "o", "a", "t"));

        mockMvc.perform(post("/api/clips/upload-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matchId\":\"" + matchId + "\",\"name\":\"cut\"}")
                        .with(roleJwt("coach")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overlayUploadUrl").value("o"));
    }

    @Test
    void deleteClip_coach_returns204() throws Exception {
        UUID clipId = UUID.randomUUID();
        Coach coach = coach();
        given(userAccessService.resolveCurrentUser(any())).willReturn(coach);

        mockMvc.perform(delete("/api/clips/{id}", clipId).with(roleJwt("coach")))
                .andExpect(status().isNoContent());
        verify(clipService).deleteClip(clipId, coach);
    }

    private static ClipResponse clipResponse(UUID clipId) {
        return new ClipResponse(clipId, null, null, "cut", List.of(), "QUEUED", null, LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    private static Coach coach() {
        Coach coach = new Coach();
        coach.setId(UUID.randomUUID());
        coach.setRole(UserRole.COACH);
        return coach;
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
