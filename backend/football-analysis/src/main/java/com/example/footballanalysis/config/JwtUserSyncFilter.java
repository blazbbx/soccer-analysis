package com.example.footballanalysis.config;

import com.example.footballanalysis.service.UserRoleSyncService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtUserSyncFilter extends OncePerRequestFilter {

    private final UserRoleSyncService userRoleSyncService;
    private final String expectedClientId;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path != null && path.equals("/api/users/register");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            var jwt = jwtAuth.getToken();
            String azp = jwt.getClaimAsString("azp");
            if (expectedClientId != null && !expectedClientId.isBlank()
                    && (azp == null || !expectedClientId.equals(azp))) {
                log.warn("Rejected JWT with azp={} (expected {})", azp, expectedClientId);
                rejectUnauthorized(response);
                return;
            }
            try {
                userRoleSyncService.syncFromJwt(jwt);
            } catch (DataIntegrityViolationException ex) {
                log.debug("JIT provisioning skipped — user already inserted by concurrent request");
            } catch (Exception ex) {
                log.warn("JIT user provisioning failed", ex);
                rejectServiceUnavailable(response);
                return;
            }

            if (!userRoleSyncService.localUserExists(jwt)) {
                log.warn("Rejected JWT for missing local user subject={}", jwt.getSubject());
                rejectUnauthorized(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static void rejectUnauthorized(HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private static void rejectServiceUnavailable(HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }
}
