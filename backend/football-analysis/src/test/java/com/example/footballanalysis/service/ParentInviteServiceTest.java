package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.model.db.ParentInvite;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.responses.InviteTokenResponse;
import com.example.footballanalysis.model.responses.TeamInviteResponse;
import com.example.footballanalysis.repository.ParentInviteRepository;
import com.example.footballanalysis.repository.PlayerParentRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParentInviteServiceTest {

    @Mock private ParentInviteRepository parentInviteRepository;
    @Mock private PlayerProfileRepository playerProfileRepository;
    @Mock private PlayerParentRepository playerParentRepository;
    @Mock private PlayerParentService playerParentService;
    @Mock private UserRepository userRepository;
    @Mock private KeycloakUserAdminService keycloakUserAdminService;
    @Mock private AuditEventService auditEventService;

    private ParentInviteService parentInviteService;

    @BeforeEach
    void setUp() {
        parentInviteService = new ParentInviteService(
                parentInviteRepository,
                playerProfileRepository,
                playerParentRepository,
                playerParentService,
                userRepository,
                keycloakUserAdminService,
                auditEventService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void create_savesInviteWithoutTeam() {
        UUID profileId = UUID.randomUUID();
        Parent parent = parent();
        PlayerProfile profile = profile(profileId);
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(playerParentRepository.existsById(any())).thenReturn(true);
        when(parentInviteRepository.save(any(ParentInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InviteTokenResponse response = parentInviteService.create(profileId, parent);

        assertThat(response.token()).isNotBlank();
        ArgumentCaptor<ParentInvite> saved = ArgumentCaptor.forClass(ParentInvite.class);
        verify(parentInviteRepository).save(saved.capture());
        assertThat(saved.getValue().getPlayerProfile()).isEqualTo(profile);
        assertThat(saved.getValue().getMaxUses()).isEqualTo(1);
        assertThat(saved.getValue().getCreatedByUserId()).isEqualTo(parent.getId());
    }

    @Test
    void create_rejectsStranger() {
        UUID profileId = UUID.randomUUID();
        Parent parent = parent();
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile(profileId)));
        when(playerParentRepository.existsById(any())).thenReturn(false);

        assertThatThrownBy(() -> parentInviteService.create(profileId, parent))
                .isInstanceOf(ForbiddenException.class);
        verify(parentInviteRepository, never()).save(any());
    }

    @Test
    void getInvite_hasChildNameAndNoTeam() {
        ParentInvite invite = invite(profile(UUID.randomUUID()));
        when(parentInviteRepository.findByToken(invite.getToken())).thenReturn(Optional.of(invite));

        TeamInviteResponse response = parentInviteService.getInvite(invite.getToken());

        assertThat(response.teamId()).isNull();
        assertThat(response.teamName()).isNull();
        assertThat(response.childFirstName()).isEqualTo("Peti");
        assertThat(response.childLastName()).isEqualTo("Kiss");
        assertThat(response.inviteKind()).isEqualTo(com.example.footballanalysis.model.db.InviteKind.PARENT);
    }

    @Test
    void accept_linksParentAndDoesNotTouchRoster() {
        Parent joining = parent();
        ParentInvite invite = invite(profile(UUID.randomUUID()));
        when(parentInviteRepository.findByTokenForUpdate(invite.getToken())).thenReturn(Optional.of(invite));
        when(parentInviteRepository.save(any(ParentInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        parentInviteService.accept(invite.getToken(), joining);

        verify(playerParentService).link(joining, invite.getPlayerProfile());
        assertThat(invite.getUsedCount()).isEqualTo(1);
    }

    private static Parent parent() {
        Parent parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setRole(UserRole.PARENT);
        return parent;
    }

    private static PlayerProfile profile(UUID id) {
        PlayerProfile profile = new PlayerProfile();
        profile.setId(id);
        profile.setFirstName("Peti");
        profile.setLastName("Kiss");
        return profile;
    }

    private static ParentInvite invite(PlayerProfile profile) {
        ParentInvite invite = new ParentInvite();
        invite.setId(UUID.randomUUID());
        invite.setPlayerProfile(profile);
        invite.setToken(UUID.randomUUID().toString());
        invite.setMaxUses(1);
        invite.setUsedCount(0);
        invite.setExpiresAt(java.time.LocalDateTime.now().plusHours(1));
        return invite;
    }
}
