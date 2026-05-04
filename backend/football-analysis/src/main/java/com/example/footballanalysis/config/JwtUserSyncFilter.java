package com.example.footballanalysis.config;

import com.example.footballanalysis.model.db.user.*;
import com.example.footballanalysis.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import org.springframework.dao.DataIntegrityViolationException;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class JwtUserSyncFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                UUID userId = parseSubject(jwt);
                if (userId != null && !userRepository.existsById(userId)) {
                    provisionUser(jwt, userId);
                }
            }
        } catch (DataIntegrityViolationException ex) {
            log.debug("JIT provisioning skipped — user already inserted by concurrent request");
        } catch (Exception ex) {
            log.warn("JIT user provisioning failed, continuing request", ex);
        }
        filterChain.doFilter(request, response);
    }

    private UUID parseSubject(Jwt jwt) {
        try {
            String subject = jwt.getSubject();
            if (subject == null || subject.isBlank()) return null;
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            log.debug("JWT subject is not a valid UUID: {}", jwt.getSubject());
            return null;
        }
    }

    private void provisionUser(Jwt jwt, UUID userId) {
        String email = jwt.getClaim("email");
        String firstName = jwt.getClaim("given_name");
        String lastName = jwt.getClaim("family_name");
        UserRole role = resolveRole(jwt);

        User user = buildUser(role);
        user.setId(userId);
        user.setEmail(email);
        user.setFirstName(firstName != null ? firstName.trim() : null);
        user.setLastName(lastName != null ? lastName.trim() : null);

        userRepository.save(user);
        log.info("JIT provisioned user {} from Keycloak JWT (role={})", userId, role);
    }

    private UserRole resolveRole(Jwt jwt) {
        // Try client roles first (resource_access.<clientId>.roles), matching the frontend
        String clientId = jwt.getClaimAsString("azp");
        if (clientId != null) {
            UserRole role = extractRoleFromResourceAccess(jwt, clientId);
            if (role != null) return role;
        }
        // Fall back to realm roles
        return extractRoleFromRealmAccess(jwt);
    }

    private UserRole extractRoleFromResourceAccess(Jwt jwt, String clientId) {
        Object resourceAccess = jwt.getClaim("resource_access");
        if (!(resourceAccess instanceof Map<?, ?> resourceMap)) return null;
        Object clientAccess = resourceMap.get(clientId);
        if (!(clientAccess instanceof Map<?, ?> clientMap)) return null;
        Object rolesObj = clientMap.get("roles");
        if (!(rolesObj instanceof Collection<?> roles)) return null;
        return matchRole(roles);
    }

    private UserRole extractRoleFromRealmAccess(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof Map<?, ?> realmMap)) return UserRole.FAN;
        Object rolesObj = realmMap.get("roles");
        if (!(rolesObj instanceof Collection<?> roles)) return UserRole.FAN;
        UserRole role = matchRole(roles);
        return role != null ? role : UserRole.FAN;
    }

    private UserRole matchRole(Collection<?> roles) {
        for (Object r : roles) {
            if (!(r instanceof String role)) continue;
            UserRole matched = switch (role.toUpperCase()) {
                case "ADMIN"  -> UserRole.ADMIN;
                case "COACH"  -> UserRole.COACH;
                case "PLAYER" -> UserRole.PLAYER;
                case "FAN"    -> UserRole.FAN;
                default       -> null;
            };
            if (matched != null) return matched;
        }
        return null;
    }

    private User buildUser(UserRole role) {
        return switch (role) {
            case ADMIN  -> { var u = new Admin();  u.setRole(UserRole.ADMIN);  yield u; }
            case COACH  -> { var u = new Coach();  u.setRole(UserRole.COACH);  yield u; }
            case PLAYER -> { var u = new Player(); u.setRole(UserRole.PLAYER); yield u; }
            case FAN    -> { var u = new Fan();    u.setRole(UserRole.FAN);    yield u; }
        };
    }
}
