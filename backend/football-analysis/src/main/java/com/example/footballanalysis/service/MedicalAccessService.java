package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.MedicalCertificate;
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

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
        return reaches(actor, profileId, Via.CLUB_ADMIN, true, null, false);
    }

    /** A coach of an ON team with this profile on the roster. Dates only. */
    public boolean isCoachRequiringProfile(User actor, UUID profileId) {
        return reaches(actor, profileId, Via.COACH, false, null, false);
    }

    /**
     * A coach of an ON team that asks for the redacted copy, with this profile on the roster, in a club that lets its
     * coaches open the file. Another club's switch opens nothing.
     */
    public boolean isCoachWithFileAccess(User actor, UUID profileId) {
        return reaches(actor, profileId, Via.COACH, true, null, false);
    }

    /** How a club admin or a coach reaches a child: through a club it runs, or a team it coaches. */
    private enum Via {
        CLUB_ADMIN,
        COACH
    }

    /**
     * The one check behind the club admin's and the coach's access: an ON team of the child, reached through the
     * caller's club ({@code CLUB_ADMIN}) or team ({@code COACH}). With {@code file}, only a team that asks for the
     * redacted copy, and for a coach one whose club lets its coaches open it. With {@code rowBound}, only a club that
     * sees {@code row} (a recipient, requiring and ready); no row then means no access. The roster is read once.
     */
    private boolean reaches(User actor, UUID profileId, Via via, boolean file, MedicalCertificate row, boolean rowBound) {
        boolean hat = via == Via.CLUB_ADMIN ? isClubAdminHat(actor) : isCoachHat(actor);
        if (profileId == null || !hat || (rowBound && row == null)) {
            return false;
        }
        List<Team> teams = medicalRequirementService.requiringTeams(profileId);
        Set<UUID> seeing = rowBound ? seeing(teams, row) : null;
        Set<UUID> clubsChecked = new HashSet<>();
        for (Team team : teams) {
            Club club = team.getClub();
            if (file && !(MedicalRequirementService.asksForFile(team)
                    && (via == Via.CLUB_ADMIN || (club != null && club.isMedicalCoachFileAccess())))) {
                continue;
            }
            if (seeing != null && (club == null || !seeing.contains(club.getId()))) {
                continue;
            }
            boolean through = via == Via.CLUB_ADMIN
                    ? club != null && clubsChecked.add(club.getId()) && userAccessService.isClubAdmin(actor, club.getId())
                    : teamMembershipQuery.isCoachOnTeam(actor.getId(), team.getId());
            if (through) {
                return true;
            }
        }
        return false;
    }

    /** The clubs among these teams' clubs that are ready and that the row is meant for. */
    private Set<UUID> seeing(List<Team> requiringTeams, MedicalCertificate row) {
        if (row.getClubIds() == null || row.getClubIds().isEmpty()) {
            return Set.of();
        }
        Set<UUID> seeing = new LinkedHashSet<>();
        medicalRequirementService.readyClubsAmong(requiringTeams).forEach(club -> seeing.add(club.getId()));
        seeing.retainAll(row.getClubIds());
        return seeing;
    }

    // ---------------------------------------------------------------- the clubs a row is meant for

    /**
     * The clubs that see this row (docs/funkciok/sportorvosi/sportorvosi-klubhoz-kotes.md): its recipients that
     * require the certificate now and are ready. Empty for no row.
     */
    public Set<UUID> seeingClubIds(UUID profileId, MedicalCertificate row) {
        if (row == null) {
            return Set.of();
        }
        return seeing(medicalRequirementService.requiringTeams(profileId), row);
    }

    /** {@link #isClubAdminRequiringProfile}, through a club that sees this row. */
    public boolean isClubAdminSeeing(User actor, UUID profileId, MedicalCertificate row) {
        return reaches(actor, profileId, Via.CLUB_ADMIN, false, row, true);
    }

    /** {@link #isClubAdminRequiringFile}, through a club that sees this row and asks for the file. */
    public boolean isClubAdminSeeingFile(User actor, UUID profileId, MedicalCertificate row) {
        return reaches(actor, profileId, Via.CLUB_ADMIN, true, row, true);
    }

    /** {@link #isCoachRequiringProfile}, on a team whose club sees this row. */
    public boolean isCoachSeeing(User actor, UUID profileId, MedicalCertificate row) {
        return reaches(actor, profileId, Via.COACH, false, row, true);
    }

    /** {@link #isCoachWithFileAccess}, on a team whose club sees this row. */
    public boolean isCoachWithFileAccessSeeing(User actor, UUID profileId, MedicalCertificate row) {
        return reaches(actor, profileId, Via.COACH, true, row, true);
    }

    /**
     * The requiring clubs through which this club admin or coach may act on the child: as club admin the clubs it
     * runs, as coach the clubs of its teams. An in-person entry or the club's own upload goes to these only, never to
     * another club: the parent decides that with the share.
     */
    public Set<UUID> actorClubsFor(User actor, UUID profileId) {
        Set<UUID> clubs = new LinkedHashSet<>(clubAdminClubsFor(actor, profileId));
        if (isCoachHat(actor)) {
            for (Team team : medicalRequirementService.requiringTeams(profileId)) {
                if (team.getClub() != null && team.getClub().getId() != null
                        && teamMembershipQuery.isCoachOnTeam(actor.getId(), team.getId())) {
                    clubs.add(team.getClub().getId());
                }
            }
        }
        return clubs;
    }

    /** The requiring clubs this club admin runs; empty for anyone else and for the platform admin. */
    public Set<UUID> clubAdminClubsFor(User actor, UUID profileId) {
        if (!isClubAdminHat(actor)) {
            return Set.of();
        }
        Set<UUID> clubs = new LinkedHashSet<>();
        medicalRequirementService.requiringClubIds(profileId).stream()
                .filter(clubId -> userAccessService.isClubAdmin(actor, clubId))
                .forEach(clubs::add);
        return clubs;
    }

    /**
     * Who may share the current certificate with a further club: those who submit in the child's name. The parent (of
     * a minor, or of an adult who allowed it), the adult player, and the child with the upload right. Not a club: the
     * data is the family's to give, one club must not pass it on to another.
     */
    public boolean canShare(User actor, PlayerProfile profile) {
        return isParent(actor, profile.getId()) || isUploadingPlayer(actor, profile);
    }

    private boolean isClubAdminHat(User actor) {
        return actor != null && userAccessService.hasHat(actor, UserRole.CLUB_ADMIN) && !isAdmin(actor);
    }

    private boolean isCoachHat(User actor) {
        return actor != null && actor.getId() != null && userAccessService.hasHat(actor, UserRole.COACH) && !isAdmin(actor);
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
