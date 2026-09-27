package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.PlayerParent;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.user.Admin;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.PlayerParentId;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.repository.ParentInviteRepository;
import com.example.footballanalysis.repository.PlayerParentRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.TeamInviteRepository;
import com.example.footballanalysis.repository.TeamRosterRepository;
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
    @InjectMocks
    private PlayerParentService playerParentService;

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
        when(playerParentRepository.existsById(org.mockito.ArgumentMatchers.any())).thenReturn(false);

        playerParentService.attachChildLogin(child, profile);

        assertThat(profile.getLinkedUser()).isEqualTo(child);
        verify(playerProfileRepository).save(profile);
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
    void detachUser_deletesProfileWhenLastParentLeavesAndThereIsNoLogin() {
        UUID parentId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        PlayerProfile profile = profile(profileId, null);
        PlayerParent link = new PlayerParent();
        link.setId(new PlayerParentId(parentId, profileId));
        link.setPlayerProfile(profile);
        when(playerProfileRepository.findByLinkedUser_Id(parentId)).thenReturn(Optional.empty());
        when(playerParentRepository.findByUser_Id(parentId)).thenReturn(List.of(link));
        when(playerParentRepository.existsByPlayerProfile_Id(profileId)).thenReturn(false);

        playerParentService.detachUser(parentId);

        verify(playerParentRepository).delete(link);
        verify(playerProfileRepository).delete(profile);
    }

    @Test
    void unlinkParent_deletesProfileWhenLastParentLeavesAndThereIsNoLogin() {
        UUID parentId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        PlayerProfile profile = profile(profileId, null);
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(playerParentRepository.existsById(new PlayerParentId(parentId, profileId))).thenReturn(true);
        when(playerParentRepository.existsByPlayerProfile_Id(profileId)).thenReturn(false);

        playerParentService.unlinkParent(profileId, parentId, admin());

        verify(playerParentRepository).deleteById(new PlayerParentId(parentId, profileId));
        verify(playerProfileRepository).delete(profile);
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
    void detachChildLogin_clearsLoginAndDeletesProfileWhenNoParentRemains() {
        UUID profileId = UUID.randomUUID();
        Player child = new Player();
        child.setId(UUID.randomUUID());
        PlayerProfile profile = profile(profileId, child);
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(playerParentRepository.existsByPlayerProfile_Id(profileId)).thenReturn(false);

        playerParentService.detachChildLogin(profileId, admin());

        assertThat(profile.getLinkedUser()).isNull();
        verify(playerProfileRepository).delete(profile);
    }

    @Test
    void detachChildLogin_rejectsWhenLoginIsMissing() {
        UUID profileId = UUID.randomUUID();
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile(profileId, null)));

        assertThatThrownBy(() -> playerParentService.detachChildLogin(profileId, admin()))
                .isInstanceOf(ConflictException.class);
        verify(playerProfileRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    private static Admin admin() {
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        admin.setRole(UserRole.ADMIN);
        return admin;
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
