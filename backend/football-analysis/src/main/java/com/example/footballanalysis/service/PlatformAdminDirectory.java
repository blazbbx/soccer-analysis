package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Who is a platform admin, for labels about other accounts (user lists, chat).
 * Keycloak's admin client role is the only source: a platform admin has no membership row,
 * so there is nothing to keep in the database. The caller's own rights still come from its token.
 */
@Service
@Slf4j
public class PlatformAdminDirectory {

    /** Admins change rarely; one page of a list should not ask Keycloak more than once. */
    static final long CACHE_NANOS = TimeUnit.SECONDS.toNanos(60);
    /** After a failed call, wait this long before asking again, so an outage does not slow every chat message. */
    static final long RETRY_AFTER_FAILURE_NANOS = TimeUnit.SECONDS.toNanos(30);

    private final KeycloakUserAdminService keycloakUserAdminService;
    private final LongSupplier nanoClock;

    private record Snapshot(Set<UUID> ids, long expiresAt) {}

    private volatile Snapshot snapshot;

    @Autowired
    public PlatformAdminDirectory(KeycloakUserAdminService keycloakUserAdminService) {
        this(keycloakUserAdminService, System::nanoTime);
    }

    PlatformAdminDirectory(KeycloakUserAdminService keycloakUserAdminService, LongSupplier nanoClock) {
        this.keycloakUserAdminService = keycloakUserAdminService;
        this.nanoClock = nanoClock;
    }

    /**
     * The platform admins' ids, cached for a minute. When Keycloak cannot be reached it keeps the last list
     * (or an empty one) and tries again only after 30 seconds: a missing label is better than a slow or failing
     * page. Never use it to grant or deny anything.
     */
    public Set<UUID> adminIdsForDisplay() {
        Snapshot current = snapshot;
        long now = nanoClock.getAsLong();
        if (current != null && now - current.expiresAt() < 0) {
            return current.ids();
        }
        try {
            Set<UUID> ids = load();
            snapshot = new Snapshot(ids, now + CACHE_NANOS);
            return ids;
        } catch (RuntimeException ex) {
            log.warn("Could not list the platform admins in Keycloak; showing the last known admin labels", ex);
            Set<UUID> kept = current != null ? current.ids() : Set.of();
            snapshot = new Snapshot(kept, now + RETRY_AFTER_FAILURE_NANOS);
            return kept;
        }
    }

    private Set<UUID> load() {
        List<String> raw = keycloakUserAdminService.listUserIdsWithClientRole(UserRole.ADMIN);
        Set<UUID> ids = new LinkedHashSet<>();
        if (raw != null) {
            for (String id : raw) {
                try {
                    if (id != null && !id.isBlank()) {
                        ids.add(UUID.fromString(id));
                    }
                } catch (IllegalArgumentException ignored) {
                    // Not one of our accounts (Keycloak ids are UUIDs).
                }
            }
        }
        return Set.copyOf(ids);
    }
}
