package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.Admin;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Fan;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserRoleSyncService {

    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final KeycloakUserAdminService keycloakUserAdminService;

    @Transactional
    public void syncFromJwt(Jwt jwt) {
        UUID userId = parseSubject(jwt);
        if (userId == null) {
            return;
        }

        UserRole jwtRole = resolveRole(jwt);
        User existing = userRepository.findById(userId).orElse(null);
        if (existing == null) {
            if (!keycloakUserAdminService.existsUser(userId.toString())) {
                log.info("Skipping JIT provisioning for {} — user no longer exists in Keycloak", userId);
                return;
            }
            provisionUser(jwt, userId, jwtRole);
            return;
        }

        if (existing.getRole() == jwtRole) {
            return;
        }

        log.info("Syncing user {} role from {} to {} based on JWT", userId, existing.getRole(), jwtRole);
        copyMemberships(userId, existing.getRole(), jwtRole);
        entityManager.createNativeQuery("UPDATE users SET user_type = :userType, role = :role WHERE id = :id")
                .setParameter("userType", jwtRole.name())
                .setParameter("role", jwtRole.name())
                .setParameter("id", userId)
                .executeUpdate();
        entityManager.clear();
    }

    public boolean localUserExists(Jwt jwt) {
        UUID userId = parseSubject(jwt);
        return userId != null && userRepository.existsById(userId);
    }

    private void provisionUser(Jwt jwt, UUID userId, UserRole role) {
        String email = jwt.getClaim("email");
        String firstName = jwt.getClaim("given_name");
        String lastName = jwt.getClaim("family_name");

        User user = buildUser(role);
        user.setId(userId);
        user.setEmail(email);
        user.setFirstName(firstName != null ? firstName.trim() : null);
        user.setLastName(lastName != null ? lastName.trim() : null);
        userRepository.save(user);
        log.info("JIT provisioned user {} from Keycloak JWT (role={})", userId, role);
    }

    private void copyMemberships(UUID userId, UserRole from, UserRole to) {
        String fromTable = membershipTable(from);
        String fromColumn = membershipIdColumn(from);
        String toTable = membershipTable(to);
        String toColumn = membershipIdColumn(to);

        if (fromTable != null && toTable != null && !fromTable.equals(toTable)) {
            entityManager.createNativeQuery("""
                            INSERT INTO %s (%s, team_id)
                            SELECT :id, team_id FROM %s WHERE %s = :id
                            AND NOT EXISTS (
                                SELECT 1 FROM %s dest
                                WHERE dest.%s = :id AND dest.team_id = %s.team_id
                            )
                            """.formatted(toTable, toColumn, fromTable, fromColumn, toTable, toColumn, fromTable))
                    .setParameter("id", userId)
                    .executeUpdate();
        }

        if (fromTable != null && (toTable == null || !fromTable.equals(toTable))) {
            entityManager.createNativeQuery("DELETE FROM %s WHERE %s = :id".formatted(fromTable, fromColumn))
                    .setParameter("id", userId)
                    .executeUpdate();
        }
    }

    private static String membershipTable(UserRole role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case PLAYER -> "player_teams";
            case COACH -> "coach_teams";
            case FAN -> "fan_favorite_teams";
            case ADMIN -> null;
        };
    }

    private static String membershipIdColumn(UserRole role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case PLAYER -> "player_id";
            case COACH -> "coach_id";
            case FAN -> "fan_id";
            case ADMIN -> null;
        };
    }

    private UUID parseSubject(Jwt jwt) {
        try {
            String subject = jwt.getSubject();
            if (subject == null || subject.isBlank()) {
                return null;
            }
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            log.debug("JWT subject is not a valid UUID: {}", jwt.getSubject());
            return null;
        }
    }

    private UserRole resolveRole(Jwt jwt) {
        String clientId = jwt.getClaimAsString("azp");
        if (clientId != null) {
            UserRole role = extractRoleFromResourceAccess(jwt, clientId);
            if (role != null) {
                return role;
            }
        }
        return extractRoleFromRealmAccess(jwt);
    }

    private UserRole extractRoleFromResourceAccess(Jwt jwt, String clientId) {
        Object resourceAccess = jwt.getClaim("resource_access");
        if (!(resourceAccess instanceof Map<?, ?> resourceMap)) {
            return null;
        }
        Object clientAccess = resourceMap.get(clientId);
        if (!(clientAccess instanceof Map<?, ?> clientMap)) {
            return null;
        }
        Object rolesObj = clientMap.get("roles");
        if (!(rolesObj instanceof Collection<?> roles)) {
            return null;
        }
        return matchRole(roles);
    }

    private UserRole extractRoleFromRealmAccess(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof Map<?, ?> realmMap)) {
            return UserRole.FAN;
        }
        Object rolesObj = realmMap.get("roles");
        if (!(rolesObj instanceof Collection<?> roles)) {
            return UserRole.FAN;
        }
        UserRole role = matchRole(roles);
        return role != null ? role : UserRole.FAN;
    }

    private UserRole matchRole(Collection<?> roles) {
        for (Object r : roles) {
            if (!(r instanceof String role)) {
                continue;
            }
            UserRole matched = switch (role.toUpperCase()) {
                case "ADMIN" -> UserRole.ADMIN;
                case "COACH" -> UserRole.COACH;
                case "PLAYER" -> UserRole.PLAYER;
                case "FAN" -> UserRole.FAN;
                default -> null;
            };
            if (matched != null) {
                return matched;
            }
        }
        return null;
    }

    private User buildUser(UserRole role) {
        return switch (role) {
            case ADMIN -> {
                var u = new Admin();
                u.setRole(UserRole.ADMIN);
                yield u;
            }
            case COACH -> {
                var u = new Coach();
                u.setRole(UserRole.COACH);
                yield u;
            }
            case PLAYER -> {
                var u = new Player();
                u.setRole(UserRole.PLAYER);
                yield u;
            }
            case FAN -> {
                var u = new Fan();
                u.setRole(UserRole.FAN);
                yield u;
            }
        };
    }
}
