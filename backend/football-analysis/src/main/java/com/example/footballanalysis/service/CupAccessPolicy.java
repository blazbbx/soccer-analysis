package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.Cup;
import com.example.footballanalysis.model.db.CupMatch;
import com.example.footballanalysis.model.db.CupTeam;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.repository.CupMatchRepository;
import com.example.footballanalysis.repository.CupRepository;
import com.example.footballanalysis.repository.CupTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Every cup permission rule in one place (plan 3.1).
 *
 * <ul>
 *   <li><b>View:</b> admin view, the creator, or anyone whose team (in the active view) is linked to a cup team.</li>
 *   <li><b>Manage:</b> admin view or the creator. A cup whose creator was deleted is managed by admins only.</li>
 *   <li><b>Create:</b> coach, club admin or admin hat.</li>
 *   <li><b>Link a real team:</b> admin, a coach of that team, or an admin of the team's club.</li>
 * </ul>
 *
 * The admin check uses the live Keycloak hat ({@link UserAccessService#isAdmin}), never a stored role (the users table has none).
 * {@code view} is the caller's active view on read endpoints. Write endpoints pass {@code null}: then the hats decide.
 */
@Component
@RequiredArgsConstructor
public class CupAccessPolicy {

    private final CupRepository cupRepository;
    private final CupTeamRepository cupTeamRepository;
    private final CupMatchRepository cupMatchRepository;
    private final UserAccessService userAccessService;

    // ===== Rules =====

    /** Admin hat, and the active view is the admin view (or no view was sent). */
    public boolean isAdminView(User actor, UserRole view) {
        return (view == null || view == UserRole.ADMIN) && userAccessService.isAdmin(actor);
    }

    public boolean isCreator(Cup cup, User actor) {
        return actor != null && actor.getId() != null
                && cup.getCreatedBy() != null
                && actor.getId().equals(cup.getCreatedBy().getId());
    }

    /** The caller's teams in the active view. With no view: all teams of all hats. */
    public Set<UUID> accessibleTeamIds(User actor, UserRole view) {
        if (actor == null || actor.getId() == null) {
            return Set.of();
        }
        Set<UUID> ids = view == null
                ? userAccessService.getAccessibleTeamIds(actor)
                : userAccessService.getAccessibleTeamIds(actor, view);
        return ids == null ? Set.of() : ids;
    }

    public boolean canView(Cup cup, User actor, UserRole view) {
        if (actor == null || actor.getId() == null) {
            return false;
        }
        if (isAdminView(actor, view) || isCreator(cup, actor)) {
            return true;
        }
        Set<UUID> accessible = accessibleTeamIds(actor, view);
        if (accessible.isEmpty() || cup.getTeams() == null) {
            return false;
        }
        return cup.getTeams().stream()
                .map(CupTeam::getRealTeam)
                .filter(Objects::nonNull)
                .map(Team::getId)
                .anyMatch(accessible::contains);
    }

    public boolean canManage(Cup cup, User actor, UserRole view) {
        return isAdminView(actor, view) || isCreator(cup, actor);
    }

    /** Same as {@link #canManage}, for a list row that only carries the creator id. */
    public boolean canManage(UUID createdById, User actor, UserRole view) {
        if (isAdminView(actor, view)) {
            return true;
        }
        return createdById != null && actor != null && createdById.equals(actor.getId());
    }

    // ===== Guards =====

    public void requireViewer(Cup cup, User actor, UserRole view) {
        if (!canView(cup, actor, view)) {
            throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied to cup.");
        }
    }

    public void requireManager(Cup cup, User actor) {
        requireActor(actor);
        if (!canManage(cup, actor, null)) {
            throw new ForbiddenException("error.auth.forbidden", new Object[0],
                    "Only cup creator or admin can modify this cup");
        }
    }

    /** CU8: the service checks the hat too, not only the route. */
    public void requireCreator(User actor) {
        requireActor(actor);
        if (userAccessService.isAdmin(actor)
                || userAccessService.hasHat(actor, UserRole.COACH)
                || userAccessService.hasHat(actor, UserRole.CLUB_ADMIN)) {
            return;
        }
        throw new ForbiddenException("error.auth.forbidden", new Object[0], "Only a coach, club admin or admin can create a cup.");
    }

    /** CU3: only a team the caller runs may be linked to a cup team. */
    public void requireLinkableRealTeam(User actor, Team realTeam) {
        if (!userAccessService.isTeamStaff(actor, realTeam)) {
            throw new ForbiddenException("error.cup.team.real_team_forbidden", new Object[0],
                    "You can only link your own team to a cup team.");
        }
    }

    // ===== Loaders =====

    public Cup requireCup(UUID cupId) {
        return cupRepository.findById(cupId)
                .orElseThrow(() -> new NotFoundException("error.cup.not_found", new Object[]{cupId}, "Cup not found: " + cupId));
    }

    /** The team must belong to this cup. */
    public CupTeam requireCupTeam(UUID cupId, UUID teamId) {
        CupTeam team = cupTeamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("error.cupteam.not_found", new Object[]{teamId}, "Cup team not found: " + teamId));
        UUID teamCupId = team.getCup() != null ? team.getCup().getId() : null;
        if (!cupId.equals(teamCupId)) {
            throw new BadRequestException("error.validation.invalid_request", new Object[]{},
                    "Team does not belong to the specified cup");
        }
        return team;
    }

    public CupMatch requireCupMatch(UUID cupId, UUID matchId) {
        return cupMatchRepository.findByIdAndCup_Id(matchId, cupId)
                .orElseThrow(() -> new NotFoundException("error.cupmatch.not_found", new Object[]{matchId}, "Match not found in this cup"));
    }

    private static void requireActor(User actor) {
        if (actor == null) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required.");
        }
    }
}
