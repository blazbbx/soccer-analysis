package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.PlayerParent;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.user.Admin;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.ClubAdmin;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.PlayerParentId;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.repository.ParentInviteRepository;
import com.example.footballanalysis.repository.PlayerParentRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.TeamInviteRepository;
import com.example.footballanalysis.repository.TeamRosterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerParentServiceTest {

    @Mock
    private PlayerParentRepository playerParentRepository;
    @Mock
    private PlayerProfileRepository playerProfileRepository;
    @Mock
    private TeamRosterRepository teamRosterRepository;
    @Mock
    private TeamInviteRepository teamInviteRepository;
    @Mock
    private ParentInviteRepository parentInviteRepository;
    @Mock
    private UserAccessService userAccessService;
    @Mock
    private UserService userService;
    @Mock
    private ExtraHatService extraHatService;
    @Mock
    private AuditEventService auditEventService;
    @InjectMocks
    private PlayerParentService playerParentService;

    @BeforeEach
    void stubAdminHat() {
        lenient().when(userAccessService.isAdmin(any())).thenAnswer(invocation -> {
            User actor = invocation.getArgument(0);
            return actor != null && actor.getRole() == UserRole.ADMIN;
        });
    }

    @Test
    void link_rejectsOwnProfile() {
        UUID userId = UUID.randomUUID();
        Player player = new Player();
        player.setId(userId);
        PlayerProfile profile = profile(UUID.randomUUID(), player);

        assertThatThrownBy(() -> playerParentService.link(player, profile))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void link_rejectsDuplicate() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Coach coach = new Coach();
        coach.setId(userId);
        coach.setRole(UserRole.COACH);
        PlayerProfile profile = profile(profileId, null);
        when(playerParentRepository.existsById(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        assertThatThrownBy(() -> playerParentService.link(coach, profile))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void link_savesPairAndKeepsCallerRole() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Coach coach = new Coach();
        coach.setId(userId);
        coach.setRole(UserRole.COACH);
        PlayerProfile profile = profile(profileId, null);
        when(playerParentRepository.existsById(org.mockito.ArgumentMatchers.any())).thenReturn(false);

        playerParentService.link(coach, profile);

        ArgumentCaptor<com.example.footballanalysis.model.db.PlayerParent> saved =
                ArgumentCaptor.forClass(com.example.footballanalysis.model.db.PlayerParent.class);
        verify(playerParentRepository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isEqualTo(coach);
        assertThat(saved.getValue().getPlayerProfile()).isEqualTo(profile);
        assertThat(coach.getRole()).isEqualTo(UserRole.COACH);
    }

    @Test
    void attachChildLogin_rejectsExistingParent() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Coach coach = new Coach();
        coach.setId(userId);
        PlayerProfile profile = profile(profileId, null);
        when(playerParentRepository.existsById(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        assertThatThrownBy(() -> playerParentService.attachChildLogin(coach, profile))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void attachChildLogin_setsLinkedUser() {
        UUID userId = UUID.randomUUID();
        Player child = new Player();
        child.setId(userId);
        PlayerProfile profile = profile(UUID.randomUUID(), null);
        when(playerProfileRepository.findByIdForUpdate(profile.getId())).thenReturn(Optional.of(profile));
        when(playerParentRepository.existsById(org.mockito.ArgumentMatchers.any())).thenReturn(false);

        playerParentService.attachChildLogin(child, profile);

        assertThat(profile.getLinkedUser()).isEqualTo(child);
        verify(playerProfileRepository).save(profile);
        verify(teamInviteRepository).deleteUnusedChildLoginInvites(profile.getId());
    }

    @Test
    void attachChildLogin_rejectsWhenLoginWasJustTaken() {
        Player child = new Player();
        child.setId(UUID.randomUUID());
        PlayerProfile profile = profile(UUID.randomUUID(), new Player());
        when(playerParentRepository.existsById(any())).thenReturn(false);
        when(playerProfileRepository.findByIdForUpdate(profile.getId())).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> playerParentService.attachChildLogin(child, profile))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getMessageCode())
                .isEqualTo("error.player_profile.login_exists");
        verify(playerProfileRepository, never()).save(any());
    }

    @Test
    void deleteIfUnowned_removesProfileWithNoParentAndNoLogin() {
        UUID profileId = UUID.randomUUID();
        PlayerProfile profile = profile(profileId, null);
        when(playerParentRepository.existsByPlayerProfile_Id(profileId)).thenReturn(false);

        playerParentService.deleteIfUnowned(profile);

        verify(teamRosterRepository).deleteByPlayerProfile_Id(profileId);
        verify(teamInviteRepository).deleteByPlayerProfile_Id(profileId);
        verify(playerProfileRepository).delete(profile);
    }

    @Test
    void deleteIfUnowned_keepsProfileThatStillHasParentOrLogin() {
        UUID profileId = UUID.randomUUID();
        Player child = new Player();
        child.setId(UUID.randomUUID());
        PlayerProfile withLogin = profile(profileId, child);
        PlayerProfile withParent = profile(UUID.randomUUID(), null);
        when(playerParentRepository.existsByPlayerProfile_Id(withParent.getId())).thenReturn(true);

        playerParentService.deleteIfUnowned(withLogin);
        playerParentService.deleteIfUnowned(withParent);

        verify(playerProfileRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void detachUser_rejectsLastParentWhenProfileHasNoLogin() {
        UUID parentId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        PlayerProfile profile = profile(profileId, null);
        PlayerParent link = new PlayerParent();
        link.setId(new PlayerParentId(parentId, profileId));
        link.setPlayerProfile(profile);
        when(playerParentRepository.findByUser_Id(parentId)).thenReturn(List.of(link));
        when(playerParentRepository.findByPlayerProfile_Id(profileId)).thenReturn(List.of(link));

        assertThatThrownBy(() -> playerParentService.detachUser(parentId))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getMessageCode())
                .isEqualTo("error.player_parent.last_without_login");

        verify(playerParentRepository, never()).delete(any());
        verify(playerProfileRepository, never()).delete(any());
    }

    @Test
    void unlinkParent_rejectsLastParentWhenProfileHasNoLogin() {
        UUID parentId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        PlayerProfile profile = profile(profileId, null);
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(playerParentRepository.existsById(new PlayerParentId(parentId, profileId))).thenReturn(true);
        when(playerParentRepository.findByPlayerProfile_Id(profileId)).thenReturn(List.of(new PlayerParent()));

        assertThatThrownBy(() -> playerParentService.unlinkParent(profileId, parentId, admin()))
                .isInstanceOf(ConflictException.class);
        verify(playerParentRepository, never()).deleteById(org.mockito.ArgumentMatchers.any());
        verify(playerProfileRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unlinkParent_removesParentWhenAnotherParentRemainsAndThereIsNoLogin() {
        UUID parentId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        PlayerProfile profile = profile(profileId, null);
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(playerParentRepository.existsById(new PlayerParentId(parentId, profileId))).thenReturn(true);
        when(playerParentRepository.findByPlayerProfile_Id(profileId)).thenReturn(List.of(new PlayerParent(), new PlayerParent()));

        playerParentService.unlinkParent(profileId, parentId, admin());

        verify(playerParentRepository).deleteById(new PlayerParentId(parentId, profileId));
        verify(playerProfileRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unlinkParent_keepsProfileWhenLoginRemains() {
        UUID parentId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Player child = new Player();
        child.setId(UUID.randomUUID());
        PlayerProfile profile = profile(profileId, child);
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(playerParentRepository.existsById(new PlayerParentId(parentId, profileId))).thenReturn(true);

        playerParentService.unlinkParent(profileId, parentId, admin());

        verify(playerParentRepository).deleteById(new PlayerParentId(parentId, profileId));
        verify(playerProfileRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unlinkParent_clubAdminOfThePlayersClubCanRemoveThatParent() {
        UUID parentId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        ClubAdmin clubAdmin = new ClubAdmin();
        clubAdmin.setId(UUID.randomUUID());
        clubAdmin.setRole(UserRole.CLUB_ADMIN);
        Player child = new Player();
        child.setId(UUID.randomUUID());
        PlayerProfile profile = profile(profileId, child);
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(userAccessService.isClubAdminOfProfile(clubAdmin, profileId)).thenReturn(true);
        when(playerParentRepository.existsById(new PlayerParentId(parentId, profileId))).thenReturn(true);

        playerParentService.unlinkParent(profileId, parentId, clubAdmin);

        verify(playerParentRepository).deleteById(new PlayerParentId(parentId, profileId));
    }

    @Test
    void unlinkParent_rejectsParent() {
        UUID profileId = UUID.randomUUID();
        Parent parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setRole(UserRole.PARENT);
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile(profileId, null)));

        assertThatThrownBy(() -> playerParentService.unlinkParent(profileId, UUID.randomUUID(), parent))
                .isInstanceOf(ForbiddenException.class);
        verify(playerParentRepository, never()).deleteById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unlinkParent_missingLink_returnsNotFound() {
        UUID profileId = UUID.randomUUID();
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile(profileId, null)));
        when(playerParentRepository.existsById(org.mockito.ArgumentMatchers.any())).thenReturn(false);

        assertThatThrownBy(() -> playerParentService.unlinkParent(profileId, UUID.randomUUID(), admin()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void unlinkParent_revokesHatWhenNoLinkRemains() {
        UUID profileId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Player child = new Player();
        child.setId(UUID.randomUUID());
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile(profileId, child)));
        when(playerParentRepository.existsById(any())).thenReturn(true);
        when(playerParentRepository.findByUser_Id(parentId)).thenReturn(List.of());

        playerParentService.unlinkParent(profileId, parentId, admin());

        verify(playerParentRepository).deleteById(new PlayerParentId(parentId, profileId));
        verify(extraHatService).revokeHat(parentId, UserRole.PARENT);
    }

    @Test
    void unlinkParent_keepsTheHatWhenParentIsTheOnlyRole() {
        UUID profileId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Player child = new Player();
        child.setId(UUID.randomUUID());
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile(profileId, child)));
        when(playerParentRepository.existsById(any())).thenReturn(true);
        when(playerParentRepository.findByUser_Id(parentId)).thenReturn(List.of());
        when(userAccessService.isOnlyRemainingHat(parentId, UserRole.PARENT)).thenReturn(true);

        playerParentService.unlinkParent(profileId, parentId, admin());

        verify(extraHatService, never()).revokeHat(any(), any());
    }

    @Test
    void unlinkParent_keepsHatWhenAnotherLinkRemains() {
        UUID profileId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Player child = new Player();
        child.setId(UUID.randomUUID());
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile(profileId, child)));
        when(playerParentRepository.existsById(any())).thenReturn(true);
        when(playerParentRepository.findByUser_Id(parentId)).thenReturn(List.of(new PlayerParent()));

        playerParentService.unlinkParent(profileId, parentId, admin());

        verify(extraHatService, never()).revokeHat(any(), any());
    }

    @Test
    void unlinkParent_withoutParentUserDoesNotCallKeycloak() {
        UUID profileId = UUID.randomUUID();
        Player child = new Player();
        child.setId(UUID.randomUUID());
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile(profileId, child)));
        when(playerParentRepository.existsById(any())).thenReturn(true);

        playerParentService.unlinkParent(profileId, null, admin());

        verify(extraHatService, never()).revokeHat(any(), any());
    }

    @Test
    void detachChildLogin_removesTheAccountAndKeepsTheRosterProfile() {
        UUID profileId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        Player child = new Player();
        child.setId(childId);
        PlayerProfile profile = profile(profileId, child);
        Admin actor = admin();
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(playerParentRepository.existsByPlayerProfile_Id(profileId)).thenReturn(true);

        playerParentService.detachChildLogin(profileId, actor);

        assertThat(profile.getLinkedUser()).isNull();
        verify(userService).deleteUser(childId, actor);
        verify(playerProfileRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void detachChildLogin_clubAdminOfThePlayersClubCanRemoveTheAccount() {
        UUID profileId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        Player child = new Player();
        child.setId(childId);
        ClubAdmin clubAdmin = new ClubAdmin();
        clubAdmin.setId(UUID.randomUUID());
        clubAdmin.setRole(UserRole.CLUB_ADMIN);
        PlayerProfile profile = profile(profileId, child);
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(userAccessService.isClubAdminOfProfile(clubAdmin, profileId)).thenReturn(true);
        when(playerParentRepository.existsByPlayerProfile_Id(profileId)).thenReturn(true);

        playerParentService.detachChildLogin(profileId, clubAdmin);

        verify(userService).deleteUser(childId, clubAdmin);
        verify(playerProfileRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void detachChildLogin_rejectsWhenNoParentRemains() {
        UUID profileId = UUID.randomUUID();
        Player child = new Player();
        child.setId(UUID.randomUUID());
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile(profileId, child)));
        when(playerParentRepository.existsByPlayerProfile_Id(profileId)).thenReturn(false);

        assertThatThrownBy(() -> playerParentService.detachChildLogin(profileId, admin()))
                .isInstanceOf(ConflictException.class);
        verify(userService, never()).deleteUser(org.mockito.ArgumentMatchers.any(UUID.class), org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void detachChildLogin_rejectsWhenLoginIsMissing() {
        UUID profileId = UUID.randomUUID();
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile(profileId, null)));

        assertThatThrownBy(() -> playerParentService.detachChildLogin(profileId, admin()))
                .isInstanceOf(ConflictException.class);
        verify(playerProfileRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unlinkParent_rejectsClubAdminWhenProfileAlsoPlaysOutsideThatClub() {
        UUID parentId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID ownClubId = UUID.randomUUID();
        UUID otherClubId = UUID.randomUUID();
        ClubAdmin clubAdmin = clubAdmin();
        Player child = new Player();
        child.setId(UUID.randomUUID());
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile(profileId, child)));
        when(userAccessService.isClubAdminOfProfile(clubAdmin, profileId)).thenReturn(true);
        when(playerProfileRepository.findTeamsByProfileId(profileId))
                .thenReturn(List.of(teamInClub(ownClubId), teamInClub(otherClubId)));
        when(userAccessService.isClubAdmin(clubAdmin, ownClubId)).thenReturn(true);
        when(userAccessService.isClubAdmin(clubAdmin, otherClubId)).thenReturn(false);

        assertThatThrownBy(() -> playerParentService.unlinkParent(profileId, parentId, clubAdmin))
                .isInstanceOf(ConflictException.class);
        verify(playerParentRepository, never()).deleteById(any());
    }

    @Test
    void unlinkParent_allowsClubAdminWhenEveryTeamIsInAnAdministeredClub() {
        UUID parentId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID firstClubId = UUID.randomUUID();
        UUID secondClubId = UUID.randomUUID();
        ClubAdmin clubAdmin = clubAdmin();
        Player child = new Player();
        child.setId(UUID.randomUUID());
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile(profileId, child)));
        when(userAccessService.isClubAdminOfProfile(clubAdmin, profileId)).thenReturn(true);
        when(playerParentRepository.existsById(new PlayerParentId(parentId, profileId))).thenReturn(true);
        when(playerProfileRepository.findTeamsByProfileId(profileId))
                .thenReturn(List.of(teamInClub(firstClubId), teamInClub(secondClubId)));
        when(userAccessService.isClubAdmin(eq(clubAdmin), eq(firstClubId))).thenReturn(true);
        when(userAccessService.isClubAdmin(eq(clubAdmin), eq(secondClubId))).thenReturn(true);

        playerParentService.unlinkParent(profileId, parentId, clubAdmin);

        verify(playerParentRepository).deleteById(new PlayerParentId(parentId, profileId));
    }

    @Test
    void unlinkParent_platformAdminIgnoresTeamsOutsideAClub() {
        UUID parentId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Player child = new Player();
        child.setId(UUID.randomUUID());
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile(profileId, child)));
        when(playerParentRepository.existsById(new PlayerParentId(parentId, profileId))).thenReturn(true);

        playerParentService.unlinkParent(profileId, parentId, admin());

        verify(playerParentRepository).deleteById(new PlayerParentId(parentId, profileId));
        verify(playerProfileRepository, never()).findTeamsByProfileId(any());
    }

    @Test
    void detachChildLogin_rejectsClubAdminWhenProfileAlsoPlaysOutsideThatClub() {
        UUID profileId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID ownClubId = UUID.randomUUID();
        UUID otherClubId = UUID.randomUUID();
        ClubAdmin clubAdmin = clubAdmin();
        Player child = new Player();
        child.setId(childId);
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile(profileId, child)));
        when(userAccessService.isClubAdminOfProfile(clubAdmin, profileId)).thenReturn(true);
        when(playerProfileRepository.findTeamsByProfileId(profileId))
                .thenReturn(List.of(teamInClub(ownClubId), teamInClub(otherClubId)));
        when(userAccessService.isClubAdmin(clubAdmin, ownClubId)).thenReturn(true);
        when(userAccessService.isClubAdmin(clubAdmin, otherClubId)).thenReturn(false);

        assertThatThrownBy(() -> playerParentService.detachChildLogin(profileId, clubAdmin))
                .isInstanceOf(ConflictException.class);
        verify(userService, never()).deleteUser(any(UUID.class), any(User.class));
    }

    @Test
    void detachChildLogin_platformAdminCanRemoveLoginWhenProfilePlaysForSeveralClubs() {
        UUID profileId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        Player child = new Player();
        child.setId(childId);
        Admin actor = admin();
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile(profileId, child)));
        when(playerParentRepository.existsByPlayerProfile_Id(profileId)).thenReturn(true);

        playerParentService.detachChildLogin(profileId, actor);

        verify(userService).deleteUser(childId, actor);
        verify(playerProfileRepository, never()).findTeamsByProfileId(any());
    }

    private static Admin admin() {
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        admin.setRole(UserRole.ADMIN);
        return admin;
    }

    private static ClubAdmin clubAdmin() {
        ClubAdmin clubAdmin = new ClubAdmin();
        clubAdmin.setId(UUID.randomUUID());
        clubAdmin.setRole(UserRole.CLUB_ADMIN);
        return clubAdmin;
    }

    private static Team teamInClub(UUID clubId) {
        Club club = new Club();
        club.setId(clubId);
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setClub(club);
        return team;
    }

    private static PlayerProfile profile(UUID id, Player linked) {
        PlayerProfile profile = new PlayerProfile();
        profile.setId(id);
        profile.setFirstName("Peti");
        profile.setLastName("Kiss");
        profile.setLinkedUser(linked);
        return profile;
    }
}
