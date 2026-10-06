package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.InviteKind;
import com.example.footballanalysis.model.db.PlayerParent;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.repository.PlayerParentRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExtraHatService {

    private final UserAccessService userAccessService;
    private final KeycloakUserAdminService keycloakUserAdminService;
    private final TeamMembershipQuery teamMembershipQuery;
    private final PlayerProfileRepository playerProfileRepository;
    private final PlayerParentRepository playerParentRepository;

    public void requireKindAllowed(User actor, InviteKind kind) {
        if (actor == null || actor.getId() == null) {
            throw new UnauthorizedException(
                    "error.auth.unauthorized",
                    new Object[0],
                    "Authentication is required to accept an invite.");
        }
        if (kind == InviteKind.CHILD_LOGIN) {
            throw new BadRequestException(
                    "error.hat.child_login",
                    new Object[0],
                    "Child login cannot be added to an existing account.");
        }
    }

    public void ensureHat(User actor, UserRole hat) {
        if (actor == null || actor.getId() == null || hat == null) {
            return;
        }
        if (userAccessService.hasHat(actor, hat)) {
            return;
        }
        grantHat(actor.getId(), hat);
    }

    public void grantHat(UUID userId, UserRole hat) {
        if (userId == null || hat == null) {
            return;
        }
        runAfterCommit(() -> keycloakUserAdminService.addClientRole(userId.toString(), hat));
    }

    public void revokeHat(UUID userId, UserRole hat) {
        if (userId == null || hat == null) {
            return;
        }
        runAfterCommit(() -> keycloakUserAdminService.removeClientRole(userId.toString(), hat));
    }

    private static final int KEYCLOAK_HAT_SYNC_ATTEMPTS = 3;
    private static final long KEYCLOAK_HAT_SYNC_RETRY_DELAY_MS = 100L;

    private void runAfterCommit(Runnable action) {
        Runnable withRetry = () -> runKeycloakHatSync(action);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        withRetry.run();
                    } catch (RuntimeException ex) {
                        log.error(
                                "Keycloak hat sync failed after commit; membership remains without the hat",
                                ex);
                    }
                }
            });
            return;
        }
        withRetry.run();
    }

    /**
     * Retry a Keycloak hat grant or revoke a few times. After commit the caller
     * logs and leaves the membership in place. With no transaction the same retry
     * runs inline, and the last failure still propagates.
     */
    private void runKeycloakHatSync(Runnable action) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= KEYCLOAK_HAT_SYNC_ATTEMPTS; attempt++) {
            try {
                action.run();
                return;
            } catch (RuntimeException ex) {
                lastFailure = ex;
                if (attempt == KEYCLOAK_HAT_SYNC_ATTEMPTS) {
                    throw ex;
                }
                log.warn(
                        "Keycloak hat sync failed (attempt {} of {}); retrying",
                        attempt,
                        KEYCLOAK_HAT_SYNC_ATTEMPTS,
                        ex);
                try {
                    Thread.sleep(KEYCLOAK_HAT_SYNC_RETRY_DELAY_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
    }

    public void requireTeamRoleFree(UUID userId, UUID teamId, UserRole taking) {
        if (userId == null || teamId == null || taking == null) {
            return;
        }
        boolean coach = teamMembershipQuery.isCoachOnTeam(userId, teamId);
        boolean fan = teamMembershipQuery.isFanOnTeam(userId, teamId);
        boolean player = playerProfileRepository.existsByLinkedUser_IdAndTeam_Id(userId, teamId);
        boolean parentOfChild = false;
        if (taking == UserRole.PLAYER) {
            List<PlayerParent> links = playerParentRepository.findLinksForParentOnTeam(userId, teamId);
            parentOfChild = links != null && !links.isEmpty();
        }
        boolean conflict = switch (taking) {
            case COACH -> fan || player;
            case FAN -> coach || player;
            case PLAYER -> coach || fan || parentOfChild;
            default -> false;
        };
        if (conflict) {
            throw new ConflictException(
                    "error.team.role_conflict",
                    new Object[0],
                    "This account already has another role on this team.");
        }
    }

    Set<UserRole> hatsOf(User actor) {
        Set<UserRole> hats = EnumSet.noneOf(UserRole.class);
        for (UserRole role : UserRole.values()) {
            if (userAccessService.hasHat(actor, role)) {
                hats.add(role);
            }
        }
        return hats;
    }
}
