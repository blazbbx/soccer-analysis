package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.User;

import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.MedicalCertificate;
import com.example.footballanalysis.model.db.MedicalEvidence;
import com.example.footballanalysis.model.db.MedicalMode;
import com.example.footballanalysis.model.db.MedicalSubmitterRole;
import com.example.footballanalysis.model.db.PlayerParentId;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.repository.PlayerParentRepository;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalAccessServiceTest {

    @Mock
    private UserAccessService userAccessService;
    @Mock
    private PlayerParentRepository playerParentRepository;
    @Mock
    private TeamMembershipQuery teamMembershipQuery;
    @Mock
    private MedicalRequirementService medicalRequirementService;
    @Mock
    private com.example.footballanalysis.repository.PlayerProfileRepository playerProfileRepository;
    @Mock
    private MedicalAgePolicy agePolicy;
    @InjectMocks
    private MedicalAccessService medicalAccess;

    private final UUID profileId = UUID.randomUUID();
    private final UUID clubA = UUID.randomUUID();
    private final UUID clubB = UUID.randomUUID();

    @org.junit.jupiter.api.BeforeEach
    void minorsByDefault() {
        PlayerProfile child = new PlayerProfile();
        child.setId(profileId);
        lenient().when(playerProfileRepository.findById(any())).thenReturn(java.util.Optional.of(child));
        lenient().when(agePolicy.isMinor(any())).thenReturn(true);
    }

    @Test
    void aClubAdminOnlyReachesTheChildThroughTheirOwnRequiringClub() {
        User adminOfB = TestHats.user(UserRole.CLUB_ADMIN);
        adminOfB.setId(UUID.randomUUID());
        TestHats.grant(adminOfB, UserRole.CLUB_ADMIN);
        when(userAccessService.hasHat(adminOfB, UserRole.CLUB_ADMIN)).thenReturn(true);
        // The child is required in club A only. Club B is OFF, so it is not among the requiring clubs.
        when(medicalRequirementService.requiringClubIds(profileId)).thenReturn(Set.of(clubA));
        lenient().when(userAccessService.isClubAdmin(adminOfB, clubB)).thenReturn(true);
        lenient().when(userAccessService.isClubAdmin(adminOfB, clubA)).thenReturn(false);

        assertThat(medicalAccess.isClubAdminRequiringProfile(adminOfB, profileId)).isFalse();
        assertThat(medicalAccess.managerRole(adminOfB, profileId)).isEmpty();

        when(userAccessService.isClubAdmin(adminOfB, clubA)).thenReturn(true);
        assertThat(medicalAccess.managerRole(adminOfB, profileId)).contains(MedicalSubmitterRole.CLUB_ADMIN);
    }

    @Test
    void aCoachOnlySeesTheDateOnAnOnTeam() {
        User coach = TestHats.user(UserRole.COACH);
        coach.setId(UUID.randomUUID());
        TestHats.grant(coach, UserRole.COACH);
        Team onTeam = MedicalRequirementServiceTest.team(MedicalRequirementServiceTest.club(MedicalMode.ON, 3), MedicalMode.INHERIT);
        when(userAccessService.hasHat(coach, UserRole.COACH)).thenReturn(true);
        when(medicalRequirementService.requiringTeams(profileId)).thenReturn(List.of(onTeam));
        when(teamMembershipQuery.isCoachOnTeam(coach.getId(), onTeam.getId())).thenReturn(false);

        assertThat(medicalAccess.isCoachRequiringProfile(coach, profileId)).isFalse();

        when(teamMembershipQuery.isCoachOnTeam(coach.getId(), onTeam.getId())).thenReturn(true);
        assertThat(medicalAccess.isCoachRequiringProfile(coach, profileId)).isTrue();

        PlayerProfile profile = new PlayerProfile();
        profile.setId(profileId);
        assertThat(medicalAccess.submitterRole(coach, profile)).contains(MedicalSubmitterRole.COACH);
    }

    @Test
    void theParentComesFirstAndDoesNotDependOnTheClub() {
        User parent = TestHats.user(UserRole.PARENT);
        parent.setId(UUID.randomUUID());
        when(playerParentRepository.existsById(new PlayerParentId(parent.getId(), profileId))).thenReturn(true);

        assertThat(medicalAccess.managerRole(parent, profileId)).contains(MedicalSubmitterRole.PARENT);
    }

    @Test
    void theChildAccountNeedsTheUploadRight() {
        User child = TestHats.user(UserRole.PLAYER);
        child.setId(UUID.randomUUID());
        PlayerProfile profile = new PlayerProfile();
        profile.setId(profileId);
        profile.setLinkedUser(child);
        lenient().when(playerParentRepository.existsById(any())).thenReturn(false);
        lenient().when(userAccessService.hasHat(eq(child), any())).thenReturn(false);

        assertThat(medicalAccess.submitterRole(child, profile)).isEmpty();

        profile.setCanUploadMedical(true);
        assertThat(medicalAccess.submitterRole(child, profile)).contains(MedicalSubmitterRole.PLAYER);
    }

    @Test
    void thePlatformAdminIsNotAManager() {
        User operator = TestHats.user(UserRole.ADMIN);
        operator.setId(UUID.randomUUID());
        TestHats.grant(operator, UserRole.ADMIN);
        PlayerProfile profile = new PlayerProfile();
        profile.setId(profileId);
        lenient().when(userAccessService.isAdmin(operator)).thenReturn(true);
        lenient().when(userAccessService.hasHat(eq(operator), any())).thenReturn(false);
        lenient().when(playerParentRepository.existsById(any())).thenReturn(false);

        assertThat(medicalAccess.managerRole(operator, profileId)).isEmpty();
        assertThat(medicalAccess.submitterRole(operator, profile)).isEmpty();
    }

    @Test
    void aClubAdminOrCoachHatOnThePlatformAdminsAccountOpensNothing() {
        User operator = TestHats.user(UserRole.ADMIN);
        operator.setId(UUID.randomUUID());
        TestHats.grant(operator, UserRole.ADMIN);
        Team onTeam = MedicalRequirementServiceTest.team(MedicalRequirementServiceTest.club(MedicalMode.ON, 3), MedicalMode.INHERIT);
        when(userAccessService.isAdmin(operator)).thenReturn(true);
        lenient().when(userAccessService.hasHat(eq(operator), any())).thenReturn(true);
        lenient().when(userAccessService.isClubAdmin(eq(operator), any())).thenReturn(true);
        lenient().when(medicalRequirementService.requiringClubIds(profileId)).thenReturn(Set.of(clubA));
        lenient().when(medicalRequirementService.requiringTeams(profileId)).thenReturn(List.of(onTeam));
        lenient().when(teamMembershipQuery.isCoachOnTeam(operator.getId(), onTeam.getId())).thenReturn(true);

        assertThat(medicalAccess.isClubAdminRequiringProfile(operator, profileId)).isFalse();
        assertThat(medicalAccess.isClubAdminRequiringFile(operator, profileId)).isFalse();
        assertThat(medicalAccess.isCoachRequiringProfile(operator, profileId)).isFalse();
        assertThat(medicalAccess.isCoachWithFileAccess(operator, profileId)).isFalse();
        assertThat(medicalAccess.managerRole(operator, profileId)).isEmpty();
    }

    @Test
    void thePlatformAdminOfItsOwnChildStaysItsParent() {
        User operator = TestHats.user(UserRole.ADMIN);
        operator.setId(UUID.randomUUID());
        TestHats.grant(operator, UserRole.ADMIN);
        PlayerProfile profile = new PlayerProfile();
        profile.setId(profileId);
        lenient().when(userAccessService.isAdmin(operator)).thenReturn(true);
        when(playerParentRepository.existsById(new PlayerParentId(operator.getId(), profileId))).thenReturn(true);

        assertThat(medicalAccess.managerRole(operator, profileId)).contains(MedicalSubmitterRole.PARENT);
    }

    // ---------------------------------------------------------------- the clubs a row is meant for

    @Test
    void aClubSeesARowOnlyAsARecipientThatIsReadyAndRequiring() {
        MedicalCertificate row = new MedicalCertificate();
        row.setClubIds(List.of(clubA, clubB));
        // Club B is a recipient but not ready (or no longer requiring); a third club is ready but no recipient.
        UUID clubC = UUID.randomUUID();
        when(medicalRequirementService.readyClubsAmong(any())).thenReturn(List.of(club(clubA), club(clubC)));

        assertThat(medicalAccess.seeingClubIds(profileId, row)).containsExactly(clubA);
        assertThat(medicalAccess.seeingClubIds(profileId, null)).isEmpty();
        assertThat(medicalAccess.seeingClubIds(profileId, new MedicalCertificate())).isEmpty();
    }

    @Test
    void theClubAdminAndTheCoachActOnARowOnlyThroughAClubThatSeesIt() {
        Club seeing = MedicalRequirementServiceTest.club(MedicalMode.ON, 3);
        seeing.setMedicalEvidence(MedicalEvidence.REDACTED_FILE);
        seeing.setMedicalCoachFileAccess(true);
        Club other = MedicalRequirementServiceTest.club(MedicalMode.ON, 3);
        other.setMedicalEvidence(MedicalEvidence.REDACTED_FILE);
        other.setMedicalCoachFileAccess(true);
        Team seeingTeam = MedicalRequirementServiceTest.team(seeing, MedicalMode.INHERIT);
        Team otherTeam = MedicalRequirementServiceTest.team(other, MedicalMode.INHERIT);
        when(medicalRequirementService.requiringTeams(profileId)).thenReturn(List.of(seeingTeam, otherTeam));
        when(medicalRequirementService.requiringClubIds(profileId)).thenReturn(Set.of(seeing.getId(), other.getId()));
        when(medicalRequirementService.readyClubsAmong(any())).thenReturn(List.of(seeing, other));
        MedicalCertificate row = new MedicalCertificate();
        row.setClubIds(List.of(seeing.getId()));

        User otherAdmin = TestHats.user(UserRole.CLUB_ADMIN);
        otherAdmin.setId(UUID.randomUUID());
        when(userAccessService.hasHat(otherAdmin, UserRole.CLUB_ADMIN)).thenReturn(true);
        lenient().when(userAccessService.isClubAdmin(otherAdmin, other.getId())).thenReturn(true);
        User otherCoach = TestHats.user(UserRole.COACH);
        otherCoach.setId(UUID.randomUUID());
        when(userAccessService.hasHat(otherCoach, UserRole.COACH)).thenReturn(true);
        lenient().when(teamMembershipQuery.isCoachOnTeam(otherCoach.getId(), otherTeam.getId())).thenReturn(true);

        // Both still reach the child through their requiring club, but not this row.
        assertThat(medicalAccess.isClubAdminRequiringProfile(otherAdmin, profileId)).isTrue();
        assertThat(medicalAccess.isClubAdminSeeing(otherAdmin, profileId, row)).isFalse();
        assertThat(medicalAccess.isClubAdminSeeingFile(otherAdmin, profileId, row)).isFalse();
        assertThat(medicalAccess.isCoachRequiringProfile(otherCoach, profileId)).isTrue();
        assertThat(medicalAccess.isCoachSeeing(otherCoach, profileId, row)).isFalse();
        assertThat(medicalAccess.isCoachWithFileAccessSeeing(otherCoach, profileId, row)).isFalse();
        assertThat(medicalAccess.actorClubsFor(otherAdmin, profileId)).containsExactly(other.getId());
        assertThat(medicalAccess.actorClubsFor(otherCoach, profileId)).containsExactly(other.getId());

        row.setClubIds(List.of(seeing.getId(), other.getId()));
        assertThat(medicalAccess.isClubAdminSeeing(otherAdmin, profileId, row)).isTrue();
        assertThat(medicalAccess.isClubAdminSeeingFile(otherAdmin, profileId, row)).isTrue();
        assertThat(medicalAccess.isCoachSeeing(otherCoach, profileId, row)).isTrue();
        assertThat(medicalAccess.isCoachWithFileAccessSeeing(otherCoach, profileId, row)).isTrue();
        assertThat(medicalAccess.isCoachSeeing(otherCoach, profileId, null)).isFalse();
    }

    private static Club club(UUID id) {
        Club club = new Club();
        club.setId(id);
        return club;
    }

    @Test
    void onlyTheFamilySideShares() {
        PlayerProfile profile = new PlayerProfile();
        profile.setId(profileId);
        User parent = TestHats.user(UserRole.PARENT);
        parent.setId(UUID.randomUUID());
        when(playerParentRepository.existsById(new PlayerParentId(parent.getId(), profileId))).thenReturn(true);
        User child = TestHats.user(UserRole.PLAYER);
        child.setId(UUID.randomUUID());
        profile.setLinkedUser(child);
        User clubAdmin = TestHats.user(UserRole.CLUB_ADMIN);
        clubAdmin.setId(UUID.randomUUID());
        lenient().when(userAccessService.hasHat(eq(clubAdmin), any())).thenReturn(true);
        lenient().when(userAccessService.isClubAdmin(eq(clubAdmin), any())).thenReturn(true);
        lenient().when(medicalRequirementService.requiringClubIds(profileId)).thenReturn(Set.of(clubA));

        assertThat(medicalAccess.canShare(parent, profile)).isTrue();
        assertThat(medicalAccess.canShare(clubAdmin, profile)).isFalse();
        assertThat(medicalAccess.canShare(child, profile)).isFalse();
        profile.setCanUploadMedical(true);
        assertThat(medicalAccess.canShare(child, profile)).isTrue();
    }
}
