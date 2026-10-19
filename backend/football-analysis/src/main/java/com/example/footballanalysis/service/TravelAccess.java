package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.model.db.PlayerParent;
import com.example.footballanalysis.model.db.PlayerParentId;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.repository.PlayerParentRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Who may offer a ride, and who may answer or take a seat for a profile.
 * Hat and club checks stay on {@link UserAccessService}.
 */
@Service
@RequiredArgsConstructor
public class TravelAccess {

    private static final Comparator<PlayerProfile> BY_NAME = Comparator
            .comparing(PlayerProfile::getLastName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(PlayerProfile::getFirstName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(PlayerProfile::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final UserAccessService userAccessService;
    private final PlayerProfileRepository playerProfileRepository;
    private final PlayerParentRepository playerParentRepository;
    private final TeamMembershipQuery teamMembershipQuery;

    public void requireCanCreateRide(User actor, Team team) {
        if (canCreateRide(actor, team)) {
            return;
        }
        if (actor != null && actor.getId() != null
                && userAccessService.hasHat(actor, UserRole.PLAYER)
                && playerProfileRepository.existsByLinkedUser_IdAndTeam_Id(actor.getId(), team.getId())) {
            throw new ForbiddenException(
                    "error.travel.ride_not_allowed",
                    new Object[0],
                    "This player is not allowed to offer rides.");
        }
        throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied.");
    }

    public boolean canCreateRide(User actor, Team team) {
        if (actor == null || actor.getId() == null || team == null || team.getId() == null) {
            return false;
        }
        if (parentOnTeam(actor, team.getId())) {
            return true;
        }
        if (userAccessService.hasHat(actor, UserRole.COACH)
                && teamMembershipQuery.isCoachOnTeam(actor.getId(), team.getId())) {
            return true;
        }
        if (userAccessService.isClubAdmin(actor, clubId(team))) {
            return true;
        }
        if (!userAccessService.hasHat(actor, UserRole.PLAYER)
                || !playerProfileRepository.existsByLinkedUser_IdAndTeam_Id(actor.getId(), team.getId())) {
            return false;
        }
        return playerProfileRepository.findByLinkedUser_IdAndTeam_Id(actor.getId(), team.getId())
                .map(this::playerMayOffer)
                .orElse(false);
    }

    /**
     * A parent grant allows a ride. A player who is already 16 and has no parent does not need that grant.
     */
    private boolean playerMayOffer(PlayerProfile profile) {
        if (profile.isCanOfferRides()) {
            return true;
        }
        return isAtLeast16(profile.getDateOfBirth())
                && profile.getId() != null
                && !playerParentRepository.existsByPlayerProfile_Id(profile.getId());
    }

    private static boolean isAtLeast16(LocalDate dateOfBirth) {
        return dateOfBirth != null
                && !dateOfBirth.isAfter(LocalDate.now())
                && Period.between(dateOfBirth, LocalDate.now()).getYears() >= 16;
    }

    /**
     * RSVP, booking and releasing a seat: admin, club admin of the team's club, a linked parent,
     * or the linked player. Coaches are not included.
     */
    public boolean canActForProfile(User actor, Team team, PlayerProfile profile) {
        if (userAccessService.isAdmin(actor)) {
            return true;
        }
        if (actor == null || actor.getId() == null || profile == null || profile.getId() == null) {
            return false;
        }
        if (userAccessService.isClubAdmin(actor, clubId(team))) {
            return true;
        }
        if (userAccessService.hasHat(actor, UserRole.PARENT)
                && playerParentRepository.existsById(new PlayerParentId(actor.getId(), profile.getId()))) {
            return true;
        }
        return isLinkedPlayer(actor, profile);
    }

    public List<PlayerProfile> callerProfiles(User actor, UUID teamId, List<PlayerProfile> roster) {
        if (actor == null || actor.getId() == null) {
            return List.of();
        }
        LinkedHashMap<UUID, PlayerProfile> found = new LinkedHashMap<>();
        if (userAccessService.hasHat(actor, UserRole.PARENT)) {
            for (PlayerParent link : parentLinks(actor, teamId)) {
                PlayerProfile profile = link.getPlayerProfile();
                if (profile != null && profile.getId() != null) {
                    found.put(profile.getId(), profile);
                }
            }
        }
        if (userAccessService.hasHat(actor, UserRole.PLAYER)) {
            List<PlayerProfile> profiles = roster == null ? List.of() : roster;
            profiles.stream()
                    .filter(profile -> profile.getLinkedUser() != null
                            && actor.getId().equals(profile.getLinkedUser().getId()))
                    .findFirst()
                    .ifPresent(profile -> found.putIfAbsent(profile.getId(), profile));
        }
        return found.values().stream()
                .sorted(BY_NAME)
                .toList();
    }

    private boolean parentOnTeam(User actor, UUID teamId) {
        return actor != null
                && actor.getId() != null
                && userAccessService.hasHat(actor, UserRole.PARENT)
                && !parentLinks(actor, teamId).isEmpty();
    }

    private List<PlayerParent> parentLinks(User actor, UUID teamId) {
        if (actor == null || actor.getId() == null) {
            return List.of();
        }
        List<PlayerParent> links = playerParentRepository.findLinksForParentOnTeam(actor.getId(), teamId);
        return links == null ? List.of() : links;
    }

    private boolean isLinkedPlayer(User actor, PlayerProfile profile) {
        return actor != null
                && actor.getId() != null
                && profile.getLinkedUser() != null
                && actor.getId().equals(profile.getLinkedUser().getId())
                && userAccessService.hasHat(actor, UserRole.PLAYER);
    }

    private static UUID clubId(Team team) {
        return team != null && team.getClub() != null ? team.getClub().getId() : null;
    }
}
