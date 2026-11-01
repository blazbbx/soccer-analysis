package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.MedicalEvidence;
import com.example.footballanalysis.model.db.MedicalMode;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.TeamRoster;
import com.example.footballanalysis.repository.MedicalCertificateRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.TeamRosterRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Decides whether a child needs a sport medical certificate. A profile needs one when it is on the
 * roster of at least one team whose effective mode is ON. A team in INHERIT follows its club, and
 * clubs start OFF, so nothing is collected until a club admin turns it on. A child needs a redacted file as well
 * when at least one of those teams asks for one (its effective evidence is REDACTED_FILE): the stricter mode wins,
 * because there is one current certificate per child.
 */
@Service
@RequiredArgsConstructor
public class MedicalRequirementService {

    public static final int DEFAULT_RETENTION_YEARS = 3;

    private final TeamRosterRepository teamRosterRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final MedicalCertificateRepository certificateRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    public static MedicalMode effectiveMode(Team team) {
        if (team == null) {
            return MedicalMode.OFF;
        }
        MedicalMode own = team.getMedicalMode();
        if (own == MedicalMode.ON || own == MedicalMode.OFF) {
            return own;
        }
        Club club = team.getClub();
        return club != null && club.getMedicalMode() == MedicalMode.ON ? MedicalMode.ON : MedicalMode.OFF;
    }

    public static boolean isOn(Team team) {
        return effectiveMode(team) == MedicalMode.ON;
    }

    /** DATE_ONLY or REDACTED_FILE: the team's own value, or its club's for INHERIT. */
    public static MedicalEvidence effectiveEvidence(Team team) {
        if (team == null) {
            return MedicalEvidence.DATE_ONLY;
        }
        Club club = team.getClub();
        return effectiveEvidence(team.getMedicalEvidence(), club != null ? club.getMedicalEvidence() : null);
    }

    /** {@link #effectiveMode(Team)} from plain values, for a change that is not written yet. */
    public static MedicalMode effectiveMode(MedicalMode teamMode, MedicalMode clubMode) {
        if (teamMode == MedicalMode.ON || teamMode == MedicalMode.OFF) {
            return teamMode;
        }
        return clubMode == MedicalMode.ON ? MedicalMode.ON : MedicalMode.OFF;
    }

    /** {@link #effectiveEvidence(Team)} from plain values, for a change that is not written yet. */
    public static MedicalEvidence effectiveEvidence(MedicalEvidence teamEvidence, MedicalEvidence clubEvidence) {
        if (teamEvidence == MedicalEvidence.DATE_ONLY || teamEvidence == MedicalEvidence.REDACTED_FILE) {
            return teamEvidence;
        }
        return clubEvidence == MedicalEvidence.REDACTED_FILE ? MedicalEvidence.REDACTED_FILE : MedicalEvidence.DATE_ONLY;
    }

    /** An ON team that asks for the redacted copy. */
    public static boolean asksForFile(Team team) {
        return isOn(team) && effectiveEvidence(team) == MedicalEvidence.REDACTED_FILE;
    }

    @Transactional(readOnly = true)
    public boolean fileRequired(UUID profileId) {
        return requiringTeams(profileId).stream().anyMatch(MedicalRequirementService::asksForFile);
    }

    @Transactional(readOnly = true)
    public Set<UUID> fileRequiredProfileIds(Collection<UUID> profileIds) {
        List<UUID> ids = clean(profileIds);
        if (ids.isEmpty()) {
            return Set.of();
        }
        Set<UUID> result = new LinkedHashSet<>();
        for (TeamRoster row : teamRosterRepository.findWithTeamAndClubByProfileIdIn(ids)) {
            if (asksForFile(row.getTeam())) {
                result.add(row.getId().getPlayerProfileId());
            }
        }
        return result;
    }

    /** Like {@link #fileRequiredProfileIds}, but from values read now. */
    @Transactional
    public Set<UUID> fileRequiredProfileIdsNow(Collection<UUID> profileIds) {
        Set<UUID> result = new LinkedHashSet<>();
        requiringNow(clean(profileIds)).forEach((profileId, requiring) -> {
            if (requiring.fileRequired()) {
                result.add(profileId);
            }
        });
        return result;
    }

    @Transactional(readOnly = true)
    public boolean isRequired(UUID profileId) {
        return profileId != null && !requiringTeams(profileId).isEmpty();
    }

    @Transactional(readOnly = true)
    public Set<UUID> requiredProfileIds(Collection<UUID> profileIds) {
        List<UUID> ids = clean(profileIds);
        if (ids.isEmpty()) {
            return Set.of();
        }
        Set<UUID> required = new LinkedHashSet<>();
        for (TeamRoster row : teamRosterRepository.findWithTeamAndClubByProfileIdIn(ids)) {
            if (isOn(row.getTeam())) {
                required.add(row.getId().getPlayerProfileId());
            }
        }
        return required;
    }

    /** The ON teams of this profile. */
    @Transactional(readOnly = true)
    public List<Team> requiringTeams(UUID profileId) {
        if (profileId == null) {
            return List.of();
        }
        return teamRosterRepository.findWithTeamAndClubByProfileIdIn(List.of(profileId)).stream()
                .map(TeamRoster::getTeam)
                .filter(MedicalRequirementService::isOn)
                .toList();
    }

    /** Clubs that have an ON team with this profile on the roster. */
    @Transactional(readOnly = true)
    public Set<UUID> requiringClubIds(UUID profileId) {
        Set<UUID> clubIds = new LinkedHashSet<>();
        for (Team team : requiringTeams(profileId)) {
            if (team.getClub() != null && team.getClub().getId() != null) {
                clubIds.add(team.getClub().getId());
            }
        }
        return clubIds;
    }

    /**
     * Locks the profiles and reloads them, flushing this transaction's own changes first. A requirement check
     * made after this ({@link #requiredProfileIdsNow}) sees every roster or mode change committed before the
     * lock, so two switches running at once cannot both still count each other as ON.
     */
    @Transactional
    public List<PlayerProfile> lockProfiles(Collection<UUID> profileIds) {
        List<UUID> ids = clean(profileIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        entityManager.flush();
        List<PlayerProfile> profiles = playerProfileRepository.findAllByIdForUpdate(ids);
        // The lock query hands back profiles this transaction already holds with their old state.
        profiles.forEach(entityManager::refresh);
        return profiles;
    }

    /** Like {@link #requiredProfileIds}, but from values read now instead of cached Team and Club entities. */
    @Transactional
    public Set<UUID> requiredProfileIdsNow(Collection<UUID> profileIds) {
        return requiringNow(clean(profileIds)).keySet();
    }

    /**
     * The requiring clubs of each required profile, read now from plain values. A caller that is about to change
     * a roster or a switch reads this first and hands it to {@link #recalculate(Collection, Map)}.
     */
    @Transactional
    public Map<UUID, Set<UUID>> requiringClubIdsNow(Collection<UUID> profileIds) {
        Map<UUID, Set<UUID>> result = new HashMap<>();
        requiringNow(clean(profileIds)).forEach((profileId, requiring) -> result.put(profileId, requiring.clubIds()));
        return result;
    }

    /** {@link #recalculate(Collection, Map)} for a change that cannot end a requirement, such as adding to a roster. */
    @Transactional
    public void recalculate(Collection<UUID> profileIds) {
        recalculate(profileIds, Map.of());
    }

    /**
     * Refreshes the cleanup values on each profile after a roster or mode change, in the
     * caller's transaction. The profiles are locked first and the requirement is read after the lock.
     * A required profile has no "not required since", no last requiring clubs, and keeps the longest retention
     * of its requiring clubs. A profile that just stopped being required keeps the clubs that required it before
     * the change ({@code requiringClubsBefore}), so a later break-glass access is still shown to them. If it still
     * has a current row, with or without a file, it also gets now as the start of its 30-day grace and keeps its last
     * retention. Without a current row there is nothing to withdraw, so the clock stays unset and the nightly cleanup
     * does not pick the profile up.
     * A required profile that no longer needs a file but still has one starts the 30-day file grace
     * ({@code medicalFileNotRequiredSince}); needing a file again, or not being required at all, clears it.
     */
    @Transactional
    public void recalculate(Collection<UUID> profileIds, Map<UUID, Set<UUID>> requiringClubsBefore) {
        List<UUID> ids = clean(profileIds);
        if (ids.isEmpty()) {
            return;
        }
        List<PlayerProfile> profiles = lockProfiles(ids);
        Map<UUID, Requiring> requiring = requiringNow(ids);
        List<UUID> startingGrace = profiles.stream()
                .filter(profile -> {
                    Requiring now = requiring.get(profile.getId());
                    return now == null ? profile.getMedicalNotRequiredSince() == null
                            : !now.fileRequired() && profile.getMedicalFileNotRequiredSince() == null;
                })
                .map(PlayerProfile::getId)
                .toList();
        Set<UUID> withCurrent = new HashSet<>();
        Set<UUID> withFile = new HashSet<>();
        if (!startingGrace.isEmpty()) {
            certificateRepository.findCurrentByProfileIdIn(startingGrace).forEach(row -> {
                withCurrent.add(row.getPlayerProfileId());
                if (row.getObjectKey() != null) {
                    withFile.add(row.getPlayerProfileId());
                }
            });
        }
        Map<UUID, Set<UUID>> before = requiringClubsBefore == null ? Map.of() : requiringClubsBefore;
        for (PlayerProfile profile : profiles) {
            Requiring now = requiring.get(profile.getId());
            if (now != null) {
                profile.setMedicalNotRequiredSince(null);
                if (now.fileRequired()) {
                    profile.setMedicalFileNotRequiredSince(null);
                } else if (withFile.contains(profile.getId())) {
                    profile.setMedicalFileNotRequiredSince(clock.instant());
                }
                now.retention().ifPresent(profile::setMedicalRetentionYears);
                if (!profile.getMedicalLastRequiredClubIds().isEmpty()) {
                    profile.setMedicalLastRequiredClubIds(new ArrayList<>());
                }
                continue;
            }
            // Not required at all: the current row's own 30-day grace takes the file with it.
            profile.setMedicalFileNotRequiredSince(null);
            Set<UUID> clubsBefore = before.getOrDefault(profile.getId(), Set.of());
            if (!clubsBefore.isEmpty()) {
                profile.setMedicalLastRequiredClubIds(new ArrayList<>(clubsBefore));
            }
            if (withCurrent.contains(profile.getId())) {
                profile.setMedicalNotRequiredSince(clock.instant());
            }
        }
    }

    /**
     * The longest retention of a required profile's requiring clubs (empty for a team without a club), those clubs,
     * and whether one of its ON teams asks for the file.
     */
    private record Requiring(Optional<Integer> retention, Set<UUID> clubIds, boolean fileRequired) {}

    private Map<UUID, Requiring> requiringNow(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Optional<Integer>> retention = new HashMap<>();
        Map<UUID, Set<UUID>> clubIds = new HashMap<>();
        Set<UUID> fileRequired = new HashSet<>();
        for (Object[] row : teamRosterRepository.findRequiringRetentionByProfileIdIn(ids, MedicalMode.ON, MedicalMode.INHERIT)) {
            UUID profileId = (UUID) row[0];
            retention.merge(profileId, Optional.ofNullable((Integer) row[1]), (left, right) -> left.isEmpty() ? right
                    : right.isEmpty() ? left : Optional.of(Math.max(left.get(), right.get())));
            Set<UUID> clubs = clubIds.computeIfAbsent(profileId, id -> new LinkedHashSet<>());
            if (row[2] != null) {
                clubs.add((UUID) row[2]);
            }
            if (effectiveEvidence((MedicalEvidence) row[3], (MedicalEvidence) row[4]) == MedicalEvidence.REDACTED_FILE) {
                fileRequired.add(profileId);
            }
        }
        Map<UUID, Requiring> result = new HashMap<>();
        retention.forEach((profileId, longest) -> result.put(profileId, new Requiring(longest, clubIds.get(profileId), fileRequired.contains(profileId))));
        return result;
    }

    private static List<UUID> clean(Collection<UUID> ids) {
        return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
    }
}
