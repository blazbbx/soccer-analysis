package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.MedicalSubmitterRole;
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

import java.util.Optional;
import java.util.UUID;

/**
 * Who may see or manage a child's sport medical certificate. The parent does not depend on the club.
 * A club admin and a coach only reach a child through a club or team where the certificate is required:
 * another club's requirement opens nothing. The platform admin is none of these, even with a club admin or coach hat:
 * with a reason it sees technical facts and can withdraw, nothing more (docs/sportorvosi-jogszeruseg.md). Its own child
 * is the exception, as for any parent. From 18 the player manages its own paper, and the parent sees sport medical data
 * only with the player's permission.
 */
@Service
@RequiredArgsConstructor
public class MedicalAccessService {

    private final UserAccessService userAccessService;
    private final PlayerParentRepository playerParentRepository;
    private final TeamMembershipQuery teamMembershipQuery;
    private final MedicalRequirementService medicalRequirementService;
    private final PlayerProfileRepository playerProfileRepository;
    private final MedicalAgePolicy agePolicy;

    /**
     * A parent of the profile, for sport medical purposes: of a minor always, of an adult only with the player's
     * permission. The rest of the parent link (rides, attendance) does not depend on this.
     */
    public boolean isParent(User actor, UUID profileId) {
        if (!isParentOfRecord(actor, profileId)) {
            return false;
        }
        PlayerProfile profile = playerProfileRepository.findById(profileId).orElse(null);
        return profile != null && (agePolicy.isMinor(profile) || profile.isMedicalParentAccessAllowed());
    }

    /** The {@code player_parents} row alone, regardless of age. */
    public boolean isParentOfRecord(User actor, UUID profileId) {
        return actor != null && actor.getId() != null && profileId != null
                && playerParentRepository.existsById(new PlayerParentId(actor.getId(), profileId));
    }

    public boolean isMinor(PlayerProfile profile) {
        return agePolicy.isMinor(profile);
    }

    public boolean isAdmin(User actor) {
        return userAccessService.isAdmin(actor);
    }

    /** A club admin of a club that has an ON team with this profile on the roster. */
    public boolean isClubAdminRequiringProfile(User actor, UUID profileId) {
        if (actor == null || profileId == null || !userAccessService.hasHat(actor, UserRole.CLUB_ADMIN) || isAdmin(actor)) {
            return false;
        }
        return medicalRequirementService.requiringClubIds(profileId).stream()
                .anyMatch(clubId -> userAccessService.isClubAdmin(actor, clubId));
    }

    /**
     * A club admin of a club that has an ON team asking for the redacted copy with this profile on the roster. Only
     * this admin opens the file: an admin whose club asks for the date only sees the result and the date.
     */
    public boolean isClubAdminRequiringFile(User actor, UUID profileId) {
        if (actor == null || profileId == null || !userAccessService.hasHat(actor, UserRole.CLUB_ADMIN) || isAdmin(actor)) {
            return false;
        }
        return medicalRequirementService.requiringTeams(profileId).stream()
                .filter(MedicalRequirementService::asksForFile)
                .map(Team::getClub)
                .anyMatch(club -> club != null && userAccessService.isClubAdmin(actor, club.getId()));
    }

    /** A coach of an ON team with this profile on the roster. Dates only. */
    public boolean isCoachRequiringProfile(User actor, UUID profileId) {
        if (actor == null || actor.getId() == null || !userAccessService.hasHat(actor, UserRole.COACH) || isAdmin(actor)) {
            return false;
        }
        for (Team team : medicalRequirementService.requiringTeams(profileId)) {
            if (teamMembershipQuery.isCoachOnTeam(actor.getId(), team.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * A coach of an ON team that asks for the redacted copy, with this profile on the roster, in a club that lets its
     * coaches open the file. Another club's switch opens nothing.
     */
    public boolean isCoachWithFileAccess(User actor, UUID profileId) {
        if (actor == null || actor.getId() == null || !userAccessService.hasHat(actor, UserRole.COACH) || isAdmin(actor)) {
            return false;
        }
        for (Team team : medicalRequirementService.requiringTeams(profileId)) {
            if (MedicalRequirementService.asksForFile(team) && team.getClub() != null && team.getClub().isMedicalCoachFileAccess()
                    && teamMembershipQuery.isCoachOnTeam(actor.getId(), team.getId())) {
                return true;
            }
        }
        return false;
    }

    public boolean isLinkedPlayer(User actor, PlayerProfile profile) {
        return actor != null && actor.getId() != null && profile != null && profile.getLinkedUser() != null
                && actor.getId().equals(profile.getLinkedUser().getId());
    }

    /** The linked account of an adult player: it manages its own paper, no upload right needed. */
    public boolean isAdultLinkedPlayer(User actor, PlayerProfile profile) {
        return isLinkedPlayer(actor, profile) && !agePolicy.isMinor(profile);
    }

    /** The linked child account with the upload right switched on, or an adult player's own account. */
    public boolean isUploadingPlayer(User actor, PlayerProfile profile) {
        return isLinkedPlayer(actor, profile) && (profile.isCanUploadMedical() || !agePolicy.isMinor(profile));
    }

    /**
     * The parent or the club admin of a requiring club: they submit, replace, withdraw, open the file,
     * and set the child's upload right. The platform admin is not a manager.
     */
    public Optional<MedicalSubmitterRole> managerRole(User actor, UUID profileId) {
        if (isParent(actor, profileId)) {
            return Optional.of(MedicalSubmitterRole.PARENT);
        }
        if (isClubAdminRequiringProfile(actor, profileId)) {
            return Optional.of(MedicalSubmitterRole.CLUB_ADMIN);
        }
        return Optional.empty();
    }

    /**
     * {@link #managerRole}, PLAYER for the child's own account with the upload right on, or COACH for a coach of an ON
     * team. The service lets the coach through only where the file is asked for.
     */
    public Optional<MedicalSubmitterRole> submitterRole(User actor, PlayerProfile profile) {
        Optional<MedicalSubmitterRole> manager = managerRole(actor, profile.getId());
        if (manager.isPresent()) {
            return manager;
        }
        if (isUploadingPlayer(actor, profile)) {
            return Optional.of(MedicalSubmitterRole.PLAYER);
        }
        return isCoachRequiringProfile(actor, profile.getId()) ? Optional.of(MedicalSubmitterRole.COACH) : Optional.empty();
    }
}
