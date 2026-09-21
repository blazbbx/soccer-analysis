package com.example.footballanalysis.config;

import com.example.footballanalysis.service.UserRoleSyncService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtUserSyncFilter")
class JwtUserSyncFilterTest {

    private static final String CLIENT_ID = "football-web-client";

    @Mock
    private UserRoleSyncService userRoleSyncService;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsTokenWithWrongAzp() throws Exception {
        JwtUserSyncFilter filter = new JwtUserSyncFilter(userRoleSyncService, CLIENT_ID);
        authenticate(jwt("other-client"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
        verify(userRoleSyncService, never()).syncFromJwt(any());
    }

    @Test
    void rejectsTokenWithMissingAzp() throws Exception {
        JwtUserSyncFilter filter = new JwtUserSyncFilter(userRoleSyncService, CLIENT_ID);
        authenticate(jwt(null));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void syncsAndContinuesWhenAzpMatches() throws Exception {
        JwtUserSyncFilter filter = new JwtUserSyncFilter(userRoleSyncService, CLIENT_ID);
        Jwt jwt = jwt(CLIENT_ID);
        authenticate(jwt);
        when(userRoleSyncService.localUserExists(jwt)).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = new MockHttpServletRequest();

        filter.doFilter(request, response, filterChain);

        verify(userRoleSyncService).syncFromJwt(jwt);
        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void continuesWhenProvisioningHitsDuplicateInsert() throws Exception {
        JwtUserSyncFilter filter = new JwtUserSyncFilter(userRoleSyncService, CLIENT_ID);
        Jwt jwt = jwt(CLIENT_ID);
        authenticate(jwt);
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(userRoleSyncService).syncFromJwt(any());
        when(userRoleSyncService.localUserExists(jwt)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void rejectsWhenLocalUserIsMissingAfterSync() throws Exception {
        JwtUserSyncFilter filter = new JwtUserSyncFilter(userRoleSyncService, CLIENT_ID);
        Jwt jwt = jwt(CLIENT_ID);
        authenticate(jwt);
        when(userRoleSyncService.localUserExists(jwt)).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(userRoleSyncService).syncFromJwt(jwt);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsWhenSyncFails() throws Exception {
        JwtUserSyncFilter filter = new JwtUserSyncFilter(userRoleSyncService, CLIENT_ID);
        Jwt jwt = jwt(CLIENT_ID);
        authenticate(jwt);
        doThrow(new IllegalStateException("db down"))
                .when(userRoleSyncService).syncFromJwt(any());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(503);
        verify(filterChain, never()).doFilter(any(), any());
        verify(userRoleSyncService, never()).localUserExists(any());
    }

    @Test
    void skipsFilterOnPublicRegisterPath() throws Exception {
        JwtUserSyncFilter filter = new JwtUserSyncFilter(userRoleSyncService, CLIENT_ID);
        authenticate(jwt(CLIENT_ID));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/users/register");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(userRoleSyncService, never()).syncFromJwt(any());
        verify(filterChain).doFilter(request, response);
    }

    private static void authenticate(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    private static Jwt jwt(String azp) {
        var builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("11111111-1111-1111-1111-111111111111")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (azp != null) {
            builder.claim("azp", azp);
        }
        return builder.build();
    }
}
