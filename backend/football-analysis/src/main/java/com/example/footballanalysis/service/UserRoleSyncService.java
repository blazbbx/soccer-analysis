package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.Admin;
import com.example.footballanalysis.model.db.user.ClubAdmin;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Fan;
import com.example.footballanalysis.model.db.user.Parent;
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
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        Set<UserRole> hats = extractAllClientRoles(jwt);
        User existing = userRepository.findById(userId).orElse(null);
        if (existing == null) {
            if (hats.isEmpty()) {
                log.info("Skipping JIT provisioning for {} — JWT has no known hats", userId);
                return;
            }
            if (!keycloakUserAdminService.existsUser(userId.toString())) {
                log.info("Skipping JIT provisioning for {} — user no longer exists in Keycloak", userId);
                return;
            }
            RequestUserHolder.store(provisionUser(jwt, userId, pickPrimary(hats)));
            return;
        }

        if (hats.isEmpty()) {
            RequestUserHolder.store(existing);
            return;
        }

        UserRole next = pickPrimary(hats);
        if (next == existing.getRole()) {
            RequestUserHolder.store(existing);
            return;
        }

        log.info("Syncing user {} primary stamp from {} to {} based on JWT hats {}", userId, existing.getRole(), next, hats);
        entityManager.createNativeQuery("UPDATE users SET user_type = :userType, role = :role WHERE id = :id")
                .setParameter("userType", next.name())
                .setParameter("role", next.name())
                .setParameter("id", userId)
                .executeUpdate();
        entityManager.clear();
        RequestUserHolder.clear();
    }

    public boolean localUserExists(Jwt jwt) {
        UUID userId = parseSubject(jwt);
        if (userId == null) {
            return false;
        }
        if (RequestUserHolder.matchingSubject(userId.toString()).isPresent()) {
            return true;
        }
        return userRepository.existsById(userId);
    }

    private User provisionUser(Jwt jwt, UUID userId, UserRole role) {
        String email = jwt.getClaim("email");
        String firstName = jwt.getClaim("given_name");
        String lastName = jwt.getClaim("family_name");

        User user = buildUser(role);
        user.setId(userId);
        user.setEmail(email);
        user.setFirstName(firstName != null ? firstName.trim() : null);
        user.setLastName(lastName != null ? lastName.trim() : null);
        User saved = userRepository.save(user);
        log.info("JIT provisioned user {} from Keycloak JWT (role={})", userId, role);
        return saved;
    }

    public Set<UserRole> extractAllClientRoles(Jwt jwt) {
        Set<UserRole> hats = EnumSet.noneOf(UserRole.class);
        String clientId = jwt.getClaimAsString("azp");
        if (clientId != null) {
            hats.addAll(rolesFromResourceAccess(jwt, clientId));
        }
        hats.addAll(rolesFromRealmAccess(jwt));
        return hats;
    }

    private static UserRole pickPrimary(Set<UserRole> hats) {
        for (UserRole ranked : List.of(
                UserRole.ADMIN, UserRole.CLUB_ADMIN, UserRole.COACH, UserRole.PLAYER, UserRole.PARENT, UserRole.FAN)) {
            if (hats.contains(ranked)) {
                return ranked;
            }
        }
        return UserRole.FAN;
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

    private Set<UserRole> rolesFromResourceAccess(Jwt jwt, String clientId) {
        Object resourceAccess = jwt.getClaim("resource_access");
        if (!(resourceAccess instanceof Map<?, ?> resourceMap)) {
            return Set.of();
        }
        Object clientAccess = resourceMap.get(clientId);
        if (!(clientAccess instanceof Map<?, ?> clientMap)) {
            return Set.of();
        }
        return matchRoles(clientMap.get("roles"));
    }

    private Set<UserRole> rolesFromRealmAccess(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof Map<?, ?> realmMap)) {
            return Set.of();
        }
        return matchRoles(realmMap.get("roles"));
    }

    private Set<UserRole> matchRoles(Object rolesObj) {
        if (!(rolesObj instanceof Collection<?> roles)) {
            return Set.of();
        }
        Set<UserRole> matched = new LinkedHashSet<>();
        for (Object r : roles) {
            if (!(r instanceof String role)) {
                continue;
            }
            UserRole parsed = parseHat(role);
            if (parsed != null) {
                matched.add(parsed);
            }
        }
        return matched;
    }

    private static UserRole parseHat(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String normalized = role.startsWith("ROLE_") ? role.substring(5) : role;
        return switch (normalized.toUpperCase()) {
            case "ADMIN" -> UserRole.ADMIN;
            case "COACH" -> UserRole.COACH;
            case "PLAYER" -> UserRole.PLAYER;
            case "PARENT" -> UserRole.PARENT;
            case "CLUB_ADMIN" -> UserRole.CLUB_ADMIN;
            case "FAN" -> UserRole.FAN;
            default -> null;
        };
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
            case PARENT -> {
                var u = new Parent();
                u.setRole(UserRole.PARENT);
                yield u;
            }
            case CLUB_ADMIN -> {
                var u = new ClubAdmin();
                u.setRole(UserRole.CLUB_ADMIN);
                yield u;
            }
        };
    }
}
