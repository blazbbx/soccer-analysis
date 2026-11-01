package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.MedicalEvidence;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.MedicalCertificate;
import com.example.footballanalysis.model.db.MedicalMode;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.TeamRoster;
import com.example.footballanalysis.model.db.TeamRosterId;
import com.example.footballanalysis.repository.MedicalCertificateRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.TeamRosterRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalRequirementServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    @Mock
    private TeamRosterRepository teamRosterRepository;
    @Mock
    private PlayerProfileRepository playerProfileRepository;
    @Mock
    private MedicalCertificateRepository certificateRepository;
    @Mock
    private EntityManager entityManager;

    private MedicalRequirementService service() {
        return new MedicalRequirementService(teamRosterRepository, playerProfileRepository, certificateRepository, entityManager,
                Clock.fixed(NOW, ZoneId.of("Europe/Budapest")));
    }

    @Test
    void inheritFollowsTheClubAndTheTeamOverrides() {
        Club on = club(MedicalMode.ON, 3);
        Club off = club(MedicalMode.OFF, 3);
        assertThat(MedicalRequirementService.effectiveMode(team(off, MedicalMode.INHERIT))).isEqualTo(MedicalMode.OFF);
        assertThat(MedicalRequirementService.effectiveMode(team(on, MedicalMode.INHERIT))).isEqualTo(MedicalMode.ON);
        assertThat(MedicalRequirementService.effectiveMode(team(off, MedicalMode.ON))).isEqualTo(MedicalMode.ON);
        assertThat(MedicalRequirementService.effectiveMode(team(on, MedicalMode.OFF))).isEqualTo(MedicalMode.OFF);
    }

    @Test
    void newClubsCollectNothingAndOneOnTeamIsEnough() {
        UUID profileId = UUID.randomUUID();
        when(teamRosterRepository.findWithTeamAndClubByProfileIdIn(anyCollection())).thenReturn(List.of(
                roster(profileId, team(club(MedicalMode.OFF, 3), MedicalMode.INHERIT))));
        assertThat(service().isRequired(profileId)).isFalse();

        when(teamRosterRepository.findWithTeamAndClubByProfileIdIn(anyCollection())).thenReturn(List.of(
                roster(profileId, team(club(MedicalMode.OFF, 3), MedicalMode.INHERIT)),
                roster(profileId, team(club(MedicalMode.ON, 3), MedicalMode.INHERIT))));
        assertThat(service().isRequired(profileId)).isTrue();
    }

    @Test
    void recalculateKeepsTheLongestRetentionOfRequiringClubsAndClearsTheGraceStart() {
        PlayerProfile profile = profile();
        profile.setMedicalNotRequiredSince(NOW.minusSeconds(3600));
        requiringRows(new Object[]{profile.getId(), 1, UUID.randomUUID(), null, null}, new Object[]{profile.getId(), 5, UUID.randomUUID(), null, null});
        when(playerProfileRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(profile));

        service().recalculate(List.of(profile.getId()));

        assertThat(profile.getMedicalNotRequiredSince()).isNull();
        assertThat(profile.getMedicalRetentionYears()).isEqualTo(5);
    }

    @Test
    void recalculateLocksAndReloadsTheProfilesBeforeReadingTheRequirement() {
        PlayerProfile profile = profile();
        requiringRows();
        when(playerProfileRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(profile));

        service().recalculate(List.of(profile.getId()));

        InOrder order = inOrder(entityManager, playerProfileRepository, teamRosterRepository);
        order.verify(entityManager).flush();
        order.verify(playerProfileRepository).findAllByIdForUpdate(List.of(profile.getId()));
        order.verify(entityManager).refresh(profile);
        order.verify(teamRosterRepository).findRequiringRetentionByProfileIdIn(any(), eq(MedicalMode.ON), eq(MedicalMode.INHERIT));
    }

    @Test
    void anOnTeamWithoutAClubStillClearsTheGraceStartAndKeepsTheRetention() {
        PlayerProfile profile = profile();
        profile.setMedicalRetentionYears(5);
        profile.setMedicalNotRequiredSince(NOW.minusSeconds(3600));
        requiringRows(new Object[]{profile.getId(), null, null, null, null});
        when(playerProfileRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(profile));

        service().recalculate(List.of(profile.getId()));

        assertThat(profile.getMedicalNotRequiredSince()).isNull();
        assertThat(profile.getMedicalRetentionYears()).isEqualTo(5);
    }

    @Test
    void recalculateStartsTheGraceOnceAndKeepsTheLastRetention() {
        PlayerProfile profile = profile();
        profile.setMedicalRetentionYears(5);
        requiringRows();
        when(playerProfileRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(profile));
        when(certificateRepository.findCurrentByProfileIdIn(List.of(profile.getId()))).thenReturn(List.of(currentFile(profile)));

        service().recalculate(List.of(profile.getId()));
        assertThat(profile.getMedicalNotRequiredSince()).isEqualTo(NOW);
        assertThat(profile.getMedicalRetentionYears()).isEqualTo(5);

        Instant earlier = NOW.minusSeconds(86_400);
        profile.setMedicalNotRequiredSince(earlier);
        service().recalculate(List.of(profile.getId()));
        assertThat(profile.getMedicalNotRequiredSince()).isEqualTo(earlier);
    }

    @Test
    void withoutACurrentRowTheGraceDoesNotStart() {
        // A child who never had a certificate (or whose certificate was already withdrawn) has nothing to clean up,
        // so the nightly job must not pick it up.
        PlayerProfile profile = profile();
        requiringRows();
        when(playerProfileRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(profile));
        when(certificateRepository.findCurrentByProfileIdIn(List.of(profile.getId()))).thenReturn(List.of());

        service().recalculate(List.of(profile.getId()));

        assertThat(profile.getMedicalNotRequiredSince()).isNull();
    }

    @Test
    void aCurrentRowWithoutAFileStartsTheGraceToo() {
        // Otherwise a date-only NEM_ALKALMAS, which has no date to run out, would stay current for good.
        PlayerProfile profile = profile();
        MedicalCertificate dateOnly = currentFile(profile);
        dateOnly.setObjectKey(null);
        requiringRows();
        when(playerProfileRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(profile));
        when(certificateRepository.findCurrentByProfileIdIn(List.of(profile.getId()))).thenReturn(List.of(dateOnly));

        service().recalculate(List.of(profile.getId()));

        assertThat(profile.getMedicalNotRequiredSince()).isEqualTo(NOW);
    }

    @Test
    void aCurrentRowWithoutAFileDoesNotStartTheFileGrace() {
        PlayerProfile profile = profile();
        MedicalCertificate dateOnly = currentFile(profile);
        dateOnly.setObjectKey(null);
        requiringRows(new Object[]{profile.getId(), 3, UUID.randomUUID(), MedicalEvidence.INHERIT, MedicalEvidence.DATE_ONLY});
        when(playerProfileRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(profile));
        when(certificateRepository.findCurrentByProfileIdIn(List.of(profile.getId()))).thenReturn(List.of(dateOnly));

        service().recalculate(List.of(profile.getId()));

        assertThat(profile.getMedicalFileNotRequiredSince()).isNull();
        assertThat(profile.getMedicalNotRequiredSince()).isNull();
    }

    @Test
    void aProfileThatStopsBeingRequiredKeepsTheClubsThatRequiredItBefore() {
        PlayerProfile profile = profile();
        UUID clubA = UUID.randomUUID();
        UUID clubB = UUID.randomUUID();
        requiringRows();
        when(playerProfileRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(profile));

        service().recalculate(List.of(profile.getId()), Map.of(profile.getId(), Set.of(clubA, clubB)));

        assertThat(profile.getMedicalLastRequiredClubIds()).containsExactlyInAnyOrder(clubA, clubB);
    }

    @Test
    void theLastRequiringClubsAreClearedOnceRequiredAgainAndKeptWhenNothingChanges() {
        PlayerProfile profile = profile();
        UUID clubA = UUID.randomUUID();
        profile.setMedicalLastRequiredClubIds(new ArrayList<>(List.of(clubA)));
        when(playerProfileRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(profile));

        // Still not required and no snapshot (the file was removed, or another child's change): the clubs stay.
        requiringRows();
        service().recalculate(List.of(profile.getId()));
        assertThat(profile.getMedicalLastRequiredClubIds()).containsExactly(clubA);

        requiringRows(new Object[]{profile.getId(), 3, UUID.randomUUID(), null, null});
        service().recalculate(List.of(profile.getId()));
        assertThat(profile.getMedicalLastRequiredClubIds()).isEmpty();
    }

    @Test
    void requiringClubIdsNowGroupsTheClubsOfEachProfile() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID clubA = UUID.randomUUID();
        UUID clubB = UUID.randomUUID();
        requiringRows(new Object[]{first, 3, clubA, null, null}, new Object[]{first, 5, clubB, null, null}, new Object[]{second, null, null, null, null});

        Map<UUID, Set<UUID>> clubs = service().requiringClubIdsNow(List.of(first, second));

        assertThat(clubs.get(first)).containsExactlyInAnyOrder(clubA, clubB);
        // An ON team without a club makes the profile required, but there is no club to show a row to.
        assertThat(clubs.get(second)).isEmpty();
    }

    @Test
    void theEvidenceInheritsFromTheClubAndTheTeamOverrides() {
        Club dates = club(MedicalMode.ON, 3);
        Club files = club(MedicalMode.ON, 3);
        files.setMedicalEvidence(MedicalEvidence.REDACTED_FILE);
        assertThat(MedicalRequirementService.effectiveEvidence(team(dates, MedicalMode.INHERIT))).isEqualTo(MedicalEvidence.DATE_ONLY);
        assertThat(MedicalRequirementService.effectiveEvidence(team(files, MedicalMode.INHERIT))).isEqualTo(MedicalEvidence.REDACTED_FILE);
        Team override = team(files, MedicalMode.INHERIT);
        override.setMedicalEvidence(MedicalEvidence.DATE_ONLY);
        assertThat(MedicalRequirementService.effectiveEvidence(override)).isEqualTo(MedicalEvidence.DATE_ONLY);
        // An OFF team asks for nothing, whatever its evidence.
        Team off = team(files, MedicalMode.OFF);
        assertThat(MedicalRequirementService.asksForFile(off)).isFalse();
    }

    @Test
    void oneOnTeamAskingForTheFileIsEnough() {
        UUID profileId = UUID.randomUUID();
        Club files = club(MedicalMode.ON, 3);
        files.setMedicalEvidence(MedicalEvidence.REDACTED_FILE);
        when(teamRosterRepository.findWithTeamAndClubByProfileIdIn(anyCollection())).thenReturn(List.of(
                roster(profileId, team(club(MedicalMode.ON, 3), MedicalMode.INHERIT))));
        assertThat(service().fileRequired(profileId)).isFalse();

        when(teamRosterRepository.findWithTeamAndClubByProfileIdIn(anyCollection())).thenReturn(List.of(
                roster(profileId, team(club(MedicalMode.ON, 3), MedicalMode.INHERIT)),
                roster(profileId, team(files, MedicalMode.INHERIT))));
        assertThat(service().fileRequired(profileId)).isTrue();
        assertThat(service().fileRequiredProfileIds(List.of(profileId))).containsExactly(profileId);
    }

    @Test
    void aRequiredChildWithAFileThatIsNoLongerAskedForStartsTheFileGraceAndAskingAgainClearsIt() {
        PlayerProfile profile = profile();
        when(playerProfileRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(profile));
        when(certificateRepository.findCurrentByProfileIdIn(anyCollection())).thenReturn(List.of(currentFile(profile)));
        requiringRows(new Object[]{profile.getId(), 3, UUID.randomUUID(), MedicalEvidence.INHERIT, MedicalEvidence.DATE_ONLY});

        service().recalculate(List.of(profile.getId()));
        assertThat(profile.getMedicalFileNotRequiredSince()).isEqualTo(NOW);
        assertThat(profile.getMedicalNotRequiredSince()).isNull();

        requiringRows(new Object[]{profile.getId(), 3, UUID.randomUUID(), MedicalEvidence.REDACTED_FILE, MedicalEvidence.DATE_ONLY});
        service().recalculate(List.of(profile.getId()));
        assertThat(profile.getMedicalFileNotRequiredSince()).isNull();
        assertThat(service().fileRequiredProfileIdsNow(List.of(profile.getId()))).containsExactly(profile.getId());
    }

    private static MedicalCertificate currentFile(PlayerProfile profile) {
        MedicalCertificate row = new MedicalCertificate();
        row.setId(UUID.randomUUID());
        row.setPlayerProfileId(profile.getId());
        row.setObjectKey("medical/" + profile.getId() + "/" + row.getId());
        return row;
    }

    private void requiringRows(Object[]... rows) {
        when(teamRosterRepository.findRequiringRetentionByProfileIdIn(anyCollection(), eq(MedicalMode.ON), eq(MedicalMode.INHERIT)))
                .thenReturn(List.of(rows));
    }

    static Club club(MedicalMode mode, int retentionYears) {
        Club club = new Club();
        club.setId(UUID.randomUUID());
        club.setMedicalMode(mode);
        club.setMedicalHistoryRetentionYears(retentionYears);
        return club;
    }

    static Team team(Club club, MedicalMode mode) {
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setClub(club);
        team.setMedicalMode(mode);
        return team;
    }

    static TeamRoster roster(UUID profileId, Team team) {
        TeamRoster roster = new TeamRoster();
        roster.setId(new TeamRosterId(profileId, team.getId()));
        roster.setTeam(team);
        return roster;
    }

    private static PlayerProfile profile() {
        PlayerProfile profile = new PlayerProfile();
        profile.setId(UUID.randomUUID());
        return profile;
    }
}
