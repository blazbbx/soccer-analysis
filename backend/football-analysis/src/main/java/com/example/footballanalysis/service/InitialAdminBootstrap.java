package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.model.db.user.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Creates the first platform admin in Keycloak on startup, from {@code INITIAL_ADMIN_EMAIL} and
 * {@code INITIAL_ADMIN_PASSWORD}. The deploy realm import carries no users, so without this nobody
 * could log in as admin on a fresh install.
 *
 * <p>Does nothing when either value is empty, or when a user with the admin role already exists.
 * The password is a starting one: Keycloak asks for a new one at the first login. The local user row
 * is created at that first login ({@link UserRoleSyncService}).
 */
@Component
@Slf4j
public class InitialAdminBootstrap {

    /** Keycloak starts after Spring in docker compose and imports the realm first; wait for it this long. */
    static final int MAX_ATTEMPTS = 30;
    static final Duration RETRY_DELAY = Duration.ofSeconds(10);

    private static final String FIRST_NAME = "Platform";
    private static final String LAST_NAME = "Admin";

    enum Outcome { NOT_CONFIGURED, ADMIN_EXISTS, CREATED, EMAIL_TAKEN }

    private final KeycloakUserAdminService keycloakUserAdminService;
    private final String email;
    private final String password;
    /**
     * An earlier try sent the create request but got no answer (a timeout, a dropped connection): Keycloak may have
     * created the account. A later "e-mail taken" is then most likely that account, not someone else's.
     */
    private volatile boolean createUnanswered;

    public InitialAdminBootstrap(
            KeycloakUserAdminService keycloakUserAdminService,
            @Value("${app.initial-admin.email:}") String email,
            @Value("${app.initial-admin.password:}") String password) {
        this.keycloakUserAdminService = keycloakUserAdminService;
        this.email = email == null ? "" : email.trim();
        this.password = password == null ? "" : password;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!configured()) {
            if (!email.isBlank() || !password.isBlank()) {
                log.warn("Initial admin is not created: INITIAL_ADMIN_EMAIL and INITIAL_ADMIN_PASSWORD must both be set");
            }
            return;
        }
        Thread worker = new Thread(this::runWithRetries, "initial-admin-bootstrap");
        worker.setDaemon(true);
        worker.start();
    }

    private void runWithRetries() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                createIfMissing();
                return;
            } catch (RuntimeException ex) {
                log.info("Initial admin: Keycloak is not ready yet (attempt {}/{}): {}",
                        attempt, MAX_ATTEMPTS, ex.getMessage());
            }
            try {
                Thread.sleep(RETRY_DELAY.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.error("Initial admin was not created: Keycloak did not answer after {} attempts. "
                + "Restart the backend once Keycloak is up.", MAX_ATTEMPTS);
    }

    /** One try. Throws when Keycloak cannot be reached or the realm is not imported yet. */
    Outcome createIfMissing() {
        if (!configured()) {
            return Outcome.NOT_CONFIGURED;
        }
        if (!keycloakUserAdminService.listUserIdsWithClientRole(UserRole.ADMIN).isEmpty()) {
            log.info("Initial admin: an admin already exists, nothing to do. "
                    + "INITIAL_ADMIN_EMAIL and INITIAL_ADMIN_PASSWORD can be removed from .env");
            return Outcome.ADMIN_EXISTS;
        }

        String userId;
        try {
            userId = keycloakUserAdminService.createUser(email, FIRST_NAME, LAST_NAME, password, UserRole.ADMIN);
        } catch (ConflictException ex) {
            // An existing account is never turned into an admin from here, except the one this bootstrap created
            // itself on a try whose answer was lost.
            java.util.Optional<String> unanswered = createUnanswered
                    ? keycloakUserAdminService.findUserIdByEmail(email) : java.util.Optional.empty();
            if (unanswered.isEmpty()) {
                log.error("Initial admin was not created: {} is already a user without the admin role. "
                        + "Use another INITIAL_ADMIN_EMAIL, or give the role in the Keycloak admin console.", email);
                return Outcome.EMAIL_TAKEN;
            }
            userId = unanswered.get();
            keycloakUserAdminService.addClientRole(userId, UserRole.ADMIN);
            log.warn("Initial admin {}: an earlier try created the account but its answer was lost. "
                    + "The admin role was given now.", email);
        } catch (RuntimeException ex) {
            createUnanswered = true;
            throw ex;
        }

        try {
            keycloakUserAdminService.requirePasswordChange(userId);
        } catch (RuntimeException ex) {
            log.warn("Initial admin {} was created, but Keycloak did not accept the forced password change. "
                    + "Change the password by hand after the first login.", email, ex);
            return Outcome.CREATED;
        }
        log.info("Initial admin {} was created. Keycloak asks for a new password at the first login.", email);
        return Outcome.CREATED;
    }

    private boolean configured() {
        return !email.isBlank() && !password.isBlank();
    }
}
