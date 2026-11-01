package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.MedicalMode;
import com.example.footballanalysis.model.db.MedicalSubmitterRole;
import com.example.footballanalysis.model.db.PlayerParentId;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.user.Admin;
import com.example.footballanalysis.model.db.user.ClubAdmin;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.Player;
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
        ClubAdmin adminOfB = new ClubAdmin();
        adminOfB.setId(UUID.randomUUID());
        adminOfB.setRole(UserRole.CLUB_ADMIN);
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
        Coach coach = new Coach();
        coach.setId(UUID.randomUUID());
        coach.setRole(UserRole.COACH);
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
        Parent parent = new Parent();
        parent.setId(UUID.randomUUID());
        when(playerParentRepository.existsById(new PlayerParentId(parent.getId(), profileId))).thenReturn(true);

        assertThat(medicalAccess.managerRole(parent, profileId)).contains(MedicalSubmitterRole.PARENT);
    }

    @Test
    void theChildAccountNeedsTheUploadRight() {
        Player child = new Player();
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
        Admin operator = new Admin();
        operator.setId(UUID.randomUUID());
        operator.setRole(UserRole.ADMIN);
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
        Admin operator = new Admin();
        operator.setId(UUID.randomUUID());
        operator.setRole(UserRole.ADMIN);
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
        Admin operator = new Admin();
        operator.setId(UUID.randomUUID());
        operator.setRole(UserRole.ADMIN);
        PlayerProfile profile = new PlayerProfile();
        profile.setId(profileId);
        lenient().when(userAccessService.isAdmin(operator)).thenReturn(true);
        when(playerParentRepository.existsById(new PlayerParentId(operator.getId(), profileId))).thenReturn(true);

        assertThat(medicalAccess.managerRole(operator, profileId)).contains(MedicalSubmitterRole.PARENT);
    }
}
