package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.User;

import com.example.footballanalysis.exception.AppException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.model.db.PlayerParentId;
import com.example.footballanalysis.model.requests.RegisterUserRequest;
import com.example.footballanalysis.model.db.ParentInvite;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.responses.InviteTokenResponse;
import com.example.footballanalysis.model.responses.ParentInviteResponse;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
    @Mock private UserAccessService userAccessService;
    @Mock private ExtraHatService extraHatService;

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-06-01T08:00:00Z"), ZoneId.of("Europe/Budapest"));

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
                auditEventService,
                userAccessService,
                extraHatService,
                CLOCK);
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void create_savesInviteWithoutTeam() {
        UUID profileId = UUID.randomUUID();
        User parent = parent();
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
        User parent = parent();
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile(profileId)));
        when(playerParentRepository.existsById(any())).thenReturn(false);

        assertThatThrownBy(() -> parentInviteService.create(profileId, parent))
                .isInstanceOf(ForbiddenException.class);
        verify(parentInviteRepository, never()).save(any());
    }

    @Test
    void create_clubAdminCannotInviteIntoAFamilyThatAlreadyHasAParent() {
        UUID profileId = UUID.randomUUID();
        User clubAdmin = TestHats.user(UserRole.CLUB_ADMIN);
        clubAdmin.setId(UUID.randomUUID());
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile(profileId)));
        when(playerParentRepository.existsById(any())).thenReturn(false);
        when(userAccessService.isClubAdminOfProfile(clubAdmin, profileId)).thenReturn(true);
        when(playerParentRepository.existsByPlayerProfile_Id(profileId)).thenReturn(true);

        assertThatThrownBy(() -> parentInviteService.create(profileId, clubAdmin))
                .isInstanceOf(ForbiddenException.class);
        verify(parentInviteRepository, never()).save(any());
    }

    @Test
    void create_clubAdminMayInviteTheFirstParent() {
        UUID profileId = UUID.randomUUID();
        User clubAdmin = TestHats.user(UserRole.CLUB_ADMIN);
        clubAdmin.setId(UUID.randomUUID());
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(profile(profileId)));
        when(playerParentRepository.existsById(any())).thenReturn(false);
        when(userAccessService.isClubAdminOfProfile(clubAdmin, profileId)).thenReturn(true);
        when(playerParentRepository.existsByPlayerProfile_Id(profileId)).thenReturn(false);
        when(parentInviteRepository.save(any(ParentInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(parentInviteService.create(profileId, clubAdmin).token()).isNotBlank();
    }

    @Test
    void accept_refusesTheInviteCreator() {
        User creator = parent();
        ParentInvite invite = invite(profile(UUID.randomUUID()));
        invite.setCreatedByUserId(creator.getId());
        when(parentInviteRepository.findByTokenForUpdate(invite.getToken())).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> parentInviteService.accept(invite.getToken(), creator))
                .isInstanceOf(ForbiddenException.class);
        verify(playerParentService, never()).link(any(), any(PlayerProfile.class));
        assertThat(invite.getUsedCount()).isZero();
    }

    @Test
    void accept_refusesAnInviteMadeByANonParentOnceTheChildHasAParent() {
        UUID profileId = UUID.randomUUID();
        UUID clubAdminId = UUID.randomUUID();
        ParentInvite invite = invite(profile(profileId));
        invite.setCreatedByUserId(clubAdminId);
        when(parentInviteRepository.findByTokenForUpdate(invite.getToken())).thenReturn(Optional.of(invite));
        when(playerParentRepository.existsById(new PlayerParentId(clubAdminId, profileId))).thenReturn(false);
        when(playerParentRepository.existsByPlayerProfile_Id(profileId)).thenReturn(true);

        assertThatThrownBy(() -> parentInviteService.accept(invite.getToken(), parent()))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getMessageCode()).isEqualTo("error.player_parent.invite_outdated"));
        verify(playerParentService, never()).link(any(), any(PlayerProfile.class));
        assertThat(invite.getUsedCount()).isZero();
    }

    @Test
    void accept_takesAParentsInviteAndTheFirstParentsInvite() {
        UUID profileId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        ParentInvite byParent = invite(profile(profileId));
        byParent.setCreatedByUserId(parentId);
        when(parentInviteRepository.findByTokenForUpdate(byParent.getToken())).thenReturn(Optional.of(byParent));
        when(parentInviteRepository.save(any(ParentInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerParentRepository.existsById(new PlayerParentId(parentId, profileId))).thenReturn(true);
        org.mockito.Mockito.lenient().when(playerParentRepository.existsByPlayerProfile_Id(profileId)).thenReturn(true);

        parentInviteService.accept(byParent.getToken(), parent());
        assertThat(byParent.getUsedCount()).isEqualTo(1);

        // The club admin's invite for a child without a parent still works.
        UUID otherProfileId = UUID.randomUUID();
        ParentInvite first = invite(profile(otherProfileId));
        first.setCreatedByUserId(UUID.randomUUID());
        when(parentInviteRepository.findByTokenForUpdate(first.getToken())).thenReturn(Optional.of(first));
        when(playerParentRepository.existsByPlayerProfile_Id(otherProfileId)).thenReturn(false);

        parentInviteService.accept(first.getToken(), parent());
        assertThat(first.getUsedCount()).isEqualTo(1);
    }

    @Test
    void register_refusesAnOutdatedInviteBeforeCreatingTheAccount() {
        UUID profileId = UUID.randomUUID();
        ParentInvite invite = invite(profile(profileId));
        invite.setCreatedByUserId(UUID.randomUUID());
        when(parentInviteRepository.findByTokenForUpdate(invite.getToken())).thenReturn(Optional.of(invite));
        when(playerParentRepository.existsByPlayerProfile_Id(profileId)).thenReturn(true);

        assertThatThrownBy(() -> parentInviteService.register(new RegisterUserRequest(
                "uj@szulo.hu", "Új", "Szülő", "Jelszo-123456", invite.getToken())))
                .isInstanceOf(ConflictException.class);
        verify(keycloakUserAdminService, never()).createUser(any(), any(), any(), any(), any());
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
        User joining = parent();
        ParentInvite invite = invite(profile(UUID.randomUUID()));
        when(parentInviteRepository.findByTokenForUpdate(invite.getToken())).thenReturn(Optional.of(invite));
        when(parentInviteRepository.save(any(ParentInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        parentInviteService.accept(invite.getToken(), joining);

        verify(playerParentService).link(joining, invite.getPlayerProfile());
        assertThat(invite.getUsedCount()).isEqualTo(1);
    }

    @Test
    void list_returnsInvitesThatCanStillBeUsed() {
        UUID profileId = UUID.randomUUID();
        User parent = parent();
        PlayerProfile child = profile(profileId);
        ParentInvite open = invite(child);
        ParentInvite used = invite(child);
        used.setUsedCount(1);
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(child));
        when(playerParentRepository.existsById(any())).thenReturn(true);
        when(parentInviteRepository.findByPlayerProfile_IdOrderByCreatedAtDesc(profileId))
                .thenReturn(List.of(open, used));

        List<ParentInviteResponse> responses = parentInviteService.list(profileId, parent);

        assertThat(responses).extracting(ParentInviteResponse::token).containsExactly(open.getToken());
        verify(parentInviteRepository, never()).deleteExpiredBefore(any(LocalDateTime.class));
    }

    @Test
    void list_omitsExpiredInvitesWithoutDeletingThem() {
        UUID profileId = UUID.randomUUID();
        User parent = parent();
        PlayerProfile child = profile(profileId);
        ParentInvite open = invite(child);
        ParentInvite expired = invite(child);
        expired.setExpiresAt(LocalDateTime.now(CLOCK).minusMinutes(1));
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(child));
        when(playerParentRepository.existsById(any())).thenReturn(true);
        when(parentInviteRepository.findByPlayerProfile_IdOrderByCreatedAtDesc(profileId))
                .thenReturn(List.of(open, expired));

        List<ParentInviteResponse> responses = parentInviteService.list(profileId, parent);

        assertThat(responses).extracting(ParentInviteResponse::token).containsExactly(open.getToken());
        verify(parentInviteRepository, never()).deleteExpiredBefore(any(LocalDateTime.class));
    }

    @Test
    void list_keepsInviteThatTheJvmClockWouldHide() {
        UUID profileId = UUID.randomUUID();
        User parent = parent();
        PlayerProfile child = profile(profileId);
        ParentInvite openOnAppClock = invite(child);
        openOnAppClock.setExpiresAt(LocalDateTime.now(CLOCK).plusMinutes(30));
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(child));
        when(playerParentRepository.existsById(any())).thenReturn(true);
        when(parentInviteRepository.findByPlayerProfile_IdOrderByCreatedAtDesc(profileId))
                .thenReturn(List.of(openOnAppClock));

        List<ParentInviteResponse> responses = parentInviteService.list(profileId, parent);

        assertThat(openOnAppClock.isExpired()).isTrue();
        assertThat(responses).extracting(ParentInviteResponse::token).containsExactly(openOnAppClock.getToken());
    }

    @Test
    void deleteExpired_removesInvitesPastTheClock() {
        when(parentInviteRepository.deleteExpiredBefore(LocalDateTime.now(CLOCK))).thenReturn(2);

        assertThat(parentInviteService.deleteExpired()).isEqualTo(2);
        verify(parentInviteRepository).deleteExpiredBefore(LocalDateTime.now(CLOCK));
    }

    @Test
    void revoke_deletesAnOpenInvite() {
        UUID profileId = UUID.randomUUID();
        User parent = parent();
        PlayerProfile child = profile(profileId);
        ParentInvite open = invite(child);
        when(playerProfileRepository.findById(profileId)).thenReturn(Optional.of(child));
        when(playerParentRepository.existsById(any())).thenReturn(true);
        when(parentInviteRepository.findById(open.getId())).thenReturn(Optional.of(open));

        parentInviteService.revoke(profileId, open.getId(), parent);

        verify(parentInviteRepository).delete(open);
        verify(auditEventService).record(
                org.mockito.ArgumentMatchers.eq("PARENT_INVITE_REVOKED"),
                org.mockito.ArgumentMatchers.eq(parent.getId()),
                org.mockito.ArgumentMatchers.eq("PROFILE"),
                org.mockito.ArgumentMatchers.eq(profileId.toString()),
                org.mockito.ArgumentMatchers.contains(open.getId().toString()));
    }

    private static User parent() {
        User parent = TestHats.user(UserRole.PARENT);
        parent.setId(UUID.randomUUID());
        TestHats.grant(parent, UserRole.PARENT);
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
