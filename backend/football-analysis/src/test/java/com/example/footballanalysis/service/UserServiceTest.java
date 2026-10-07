package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.ClubAdminMembership;
import com.example.footballanalysis.model.db.user.Admin;
import com.example.footballanalysis.model.db.user.ClubAdmin;
import com.example.footballanalysis.model.db.user.Fan;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.UpdateUserRequest;
import com.example.footballanalysis.repository.ClipRepository;
import com.example.footballanalysis.repository.ClubAdminMembershipRepository;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.MatchSquadMemberRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import com.example.footballanalysis.repository.TeamMessageRepository;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService update tesztek")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamMessageRepository teamMessageRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private ClipRepository clipRepository;

    @Mock
    private MatchSquadMemberRepository matchSquadMemberRepository;

    @Mock
    private KeycloakUserAdminService keycloakUserAdminService;

    @Mock
    private AuditEventService auditEventService;

    @Mock
    private PlayerProfileRepository playerProfileRepository;

    @Mock
    private PlayerParentService playerParentService;

    @Mock
    private ClubAdminMembershipRepository clubAdminMembershipRepository;

    @Mock
    private UserAccessService userAccessService;

    @Mock
    private TeamMembershipQuery teamMembershipQuery;

    @Mock
    private TeamRepository teamRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void getAllUsers_returnsEmptyPageWhenTheRepositoryIsEmpty() {
        when(userRepository.findPage(any())).thenReturn(org.springframework.data.domain.Page.empty());

        var users = userService.getAllUsers(null, null);

        assertThat(users.items()).isEmpty();
        assertThat(users.total()).isZero();
        assertThat(users.totalPages()).isZero();
        assertThat(users.page()).isEqualTo(1);
    }

    @Test
    void getAllUsers_secondPageLeavesTeamsEmptyAndKeepsTheAdminCount() {
        Fan first = fan(UUID.randomUUID());
        first.setLastName("Alma");
        Fan second = fan(UUID.randomUUID());
        second.setLastName("Beke");
        Fan third = fan(UUID.randomUUID());
        third.setLastName("Ceres");
        List<Fan> all = List.of(first, second, third);
        when(userRepository.findPage(any())).thenAnswer(invocation -> {
            org.springframework.data.domain.Pageable pageable = invocation.getArgument(0);
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), all.size());
            List<Fan> slice = start >= all.size() ? List.of() : all.subList(start, end);
            return new org.springframework.data.domain.PageImpl<>(slice, pageable, all.size());
        });
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(2L);

        var page = userService.getAllUsers(2, 2);

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.adminCount()).isEqualTo(2);
        assertThat(page.items()).extracting(com.example.footballanalysis.model.responses.UserResponse::id)
                .containsExactly(third.getId());
        assertThat(page.items().getFirst().teams()).isNull();
        verify(userRepository, never()).findPageMatching(any(), any());
    }

    @Test
    void getAllUsers_searchUsesTheNameAndEmailQuery() {
        when(userRepository.findPageMatching(eq("%kov%"), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(0L);

        var page = userService.getAllUsers(1, 20, " Kov ");

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
        verify(userRepository, never()).findPage(any());
    }

    @Test
    void updateUser_updatesUserInDbAndKeycloak() {
        UUID userId = UUID.randomUUID();

        Admin user = new Admin();
        user.setId(userId);
        user.setEmail("admin@test.com");
        user.setFirstName("Old");
        user.setLastName("Name");
        user.setRole(UserRole.ADMIN);
        user.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("sub", userId.toString())
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(3600))
                .build();

        UpdateUserRequest req = new UpdateUserRequest("New", "Surname");

        var response = userService.updateUser(userId, req, jwt);

        verify(keycloakUserAdminService).updateUser(userId.toString(), "New", "Surname");

        ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFirstName()).isEqualTo("New");
        assertThat(captor.getValue().getLastName()).isEqualTo("Surname");
        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.firstName()).isEqualTo("New");
        assertThat(response.lastName()).isEqualTo("Surname");
    }


    @Test
    void updateUser_savesDateOfBirthOnLinkedProfileWhenStampIsNotPlayer() {
        UUID userId = UUID.randomUUID();
        Admin user = new Admin();
        user.setId(userId);
        user.setEmail("admin@test.com");
        user.setFirstName("Old");
        user.setLastName("Name");
        user.setRole(UserRole.ADMIN);
        user.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        PlayerProfile profile = new PlayerProfile();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerProfileRepository.findByLinkedUser_Id(userId)).thenReturn(Optional.of(profile));

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("sub", userId.toString())
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(3600))
                .build();

        userService.updateUser(userId, new UpdateUserRequest("New", "Surname", LocalDate.of(2012, 3, 4)), jwt);

        assertThat(profile.getDateOfBirth()).isEqualTo(LocalDate.of(2012, 3, 4));
        verify(playerProfileRepository).save(profile);
    }

    @Test
    void updateMyUser_usesJwtSubjectAsCurrentUserId() {
        UUID userId = UUID.randomUUID();

        Admin user = new Admin();
        user.setId(userId);
        user.setEmail("admin@test.com");
        user.setFirstName("Old");
        user.setLastName("Name");
        user.setRole(UserRole.ADMIN);
        user.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("sub", userId.toString())
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(3600))
                .build();

        UpdateUserRequest req = new UpdateUserRequest("Self", "Update");

        var response = userService.updateMyUser(req, jwt);

        verify(keycloakUserAdminService).updateUser(userId.toString(), "Self", "Update");
        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.firstName()).isEqualTo("Self");
        assertThat(response.lastName()).isEqualTo("Update");
    }

    @Test
    void updateMyUser_rejectsMissingSubject() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(3600))
                .build();

        assertThatThrownBy(() -> userService.updateMyUser(new UpdateUserRequest("A", "B"), jwt))
                .isInstanceOf(com.example.footballanalysis.exception.BadRequestException.class);
    }

    @Test
    void updateUser_clubAdminCannotEditUserOutsideTheirClub() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ClubAdmin actor = new ClubAdmin();
        actor.setId(actorId);
        actor.setRole(UserRole.CLUB_ADMIN);
        Fan target = new Fan();
        target.setId(targetId);
        target.setRole(UserRole.FAN);

        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userAccessService.hasHat(actor, UserRole.CLUB_ADMIN)).thenReturn(true);
        when(clubAdminMembershipRepository.findByUser_Id(actorId)).thenReturn(List.of());

        assertThatThrownBy(() -> userService.updateUser(targetId, new UpdateUserRequest("A", "B"), jwtFor(actorId)))
                .isInstanceOf(ForbiddenException.class);

        verify(keycloakUserAdminService, never()).updateUser(any(), any(), any());
    }

    @Test
    void updateUser_clubAdminCannotEditKeycloakAdminWhoseStampIsNotAdmin() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        ClubAdmin actor = clubAdmin(actorId);
        Fan target = fan(targetId);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userAccessService.hasHat(actor, UserRole.CLUB_ADMIN)).thenReturn(true);
        when(keycloakUserAdminService.hasClientRole(targetId.toString(), UserRole.ADMIN)).thenReturn(true);
        shareClub(actor, target, clubId);

        assertThatThrownBy(() -> userService.updateUser(targetId, new UpdateUserRequest("A", "B"), jwtFor(actorId)))
                .isInstanceOf(ForbiddenException.class);

        verify(keycloakUserAdminService, never()).updateUser(any(), any(), any());
    }

    @Test
    void updateUser_clubAdminCanEditClubMemberWhoIsNotAKeycloakAdmin() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        ClubAdmin actor = clubAdmin(actorId);
        Fan target = fan(targetId);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userAccessService.hasHat(actor, UserRole.CLUB_ADMIN)).thenReturn(true);
        when(keycloakUserAdminService.hasClientRole(targetId.toString(), UserRole.ADMIN)).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(teamMembershipQuery.findCoachTeamIds(targetId)).thenReturn(List.of());
        when(teamMembershipQuery.findFanTeamIds(targetId)).thenReturn(List.of());
        when(playerProfileRepository.findTeamIdsByLinkedUserId(targetId)).thenReturn(List.of());
        shareClub(actor, target, clubId);

        userService.updateUser(targetId, new UpdateUserRequest("A", "B"), jwtFor(actorId));

        verify(keycloakUserAdminService).updateUser(targetId.toString(), "A", "B");
    }

    @Test
    void updateUser_clubAdminEditFallsBackToLocalStampWhenKeycloakAdminCheckFails() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        ClubAdmin actor = clubAdmin(actorId);
        Admin target = new Admin();
        target.setId(targetId);
        target.setRole(UserRole.ADMIN);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userAccessService.hasHat(actor, UserRole.CLUB_ADMIN)).thenReturn(true);
        when(keycloakUserAdminService.hasClientRole(targetId.toString(), UserRole.ADMIN))
                .thenThrow(new RuntimeException("kc down"));
        shareClub(actor, target, clubId);

        assertThatThrownBy(() -> userService.updateUser(targetId, new UpdateUserRequest("A", "B"), jwtFor(actorId)))
                .isInstanceOf(ForbiddenException.class);

        Fan member = fan(targetId);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(member));
        shareClub(actor, member, clubId);

        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(teamMembershipQuery.findCoachTeamIds(targetId)).thenReturn(List.of());
        when(teamMembershipQuery.findFanTeamIds(targetId)).thenReturn(List.of());
        when(playerProfileRepository.findTeamIdsByLinkedUserId(targetId)).thenReturn(List.of());

        userService.updateUser(targetId, new UpdateUserRequest("A", "B"), jwtFor(actorId));

        verify(keycloakUserAdminService).updateUser(targetId.toString(), "A", "B");
    }

    private static ClubAdmin clubAdmin(UUID actorId) {
        ClubAdmin actor = new ClubAdmin();
        actor.setId(actorId);
        actor.setRole(UserRole.CLUB_ADMIN);
        return actor;
    }

    private static Fan fan(UUID targetId) {
        Fan target = new Fan();
        target.setId(targetId);
        target.setRole(UserRole.FAN);
        return target;
    }

    private void shareClub(com.example.footballanalysis.model.db.user.User actor, com.example.footballanalysis.model.db.user.User target, UUID clubId) {
        Club club = new Club();
        club.setId(clubId);
        ClubAdminMembership membership = new ClubAdminMembership();
        membership.setUser(actor);
        membership.setClub(club);
        when(clubAdminMembershipRepository.findByUser_Id(actor.getId())).thenReturn(List.of(membership));
        when(userRepository.findUserIdInClub(clubId, target.getId())).thenReturn(List.of(target.getId()));
    }

    @Test
    void deleteUser_clearsChatAndOtherReferencesBeforeDeletingUser() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Jwt jwt = jwtFor(actorId);

        Fan fan = new Fan();
        fan.setId(userId);
        fan.setRole(UserRole.FAN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(fan));

        userService.deleteUser(userId, jwt);

        InOrder order = inOrder(
                teamMessageRepository,
                matchRepository,
                clipRepository,
                matchSquadMemberRepository,
                playerParentService,
                teamMembershipQuery,
                userRepository,
                keycloakUserAdminService
        );
        order.verify(teamMessageRepository).deleteBySenderId(userId);
        order.verify(matchRepository).clearUploadedByUserId(userId);
        order.verify(clipRepository).clearCreatedByUserId(userId);
        order.verify(matchSquadMemberRepository).deleteAllByPlayer_Id(userId);
        order.verify(playerParentService).detachUser(userId);
        order.verify(teamMembershipQuery).deleteCoachTeamsForUser(userId);
        order.verify(teamMembershipQuery).deleteFanTeamsForUser(userId);
        order.verify(userRepository).deleteById(userId);
        order.verify(userRepository).flush();
        order.verify(keycloakUserAdminService).deleteUser(userId.toString());
        verify(auditEventService).record(
                "USER_DELETED",
                actorId,
                null,
                "USER",
                userId.toString(),
                "deleted_from_local_db=true, deleted_from_keycloak=true"
        );
    }

    @Test
    void deleteUser_throwsWhenUserMissing() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(userId, jwtFor(UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class);

        verify(teamMessageRepository, never()).deleteBySenderId(any());
        verify(userRepository, never()).deleteById(any());
        verify(keycloakUserAdminService, never()).deleteUser(any());
    }

    @Test
    void deleteUser_rejectsLastRemainingAdmin() {
        UUID adminId = UUID.randomUUID();
        Admin admin = new Admin();
        admin.setId(adminId);
        admin.setRole(UserRole.ADMIN);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(keycloakUserAdminService.listUserIdsWithClientRole(UserRole.ADMIN)).thenReturn(List.of(adminId.toString()));
        when(userRepository.findIdsByRole(UserRole.ADMIN)).thenReturn(List.of(adminId));

        assertThatThrownBy(() -> userService.deleteUser(adminId, jwtFor(UUID.randomUUID())))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.user.last_admin");

        verify(userRepository, never()).deleteById(any());
        verify(keycloakUserAdminService, never()).deleteUser(any());
        verify(teamMessageRepository, never()).deleteBySenderId(any());
    }

    @Test
    void deleteUser_rejectsKeycloakAdminWhoseStampIsNotAdmin() {
        UUID userId = UUID.randomUUID();
        Fan fan = new Fan();
        fan.setId(userId);
        fan.setRole(UserRole.FAN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(fan));
        when(keycloakUserAdminService.hasClientRole(userId.toString(), UserRole.ADMIN)).thenReturn(true);
        when(keycloakUserAdminService.listUserIdsWithClientRole(UserRole.ADMIN)).thenReturn(List.of(userId.toString()));
        when(userRepository.findIdsByRole(UserRole.ADMIN)).thenReturn(List.of());

        assertThatThrownBy(() -> userService.deleteUser(userId, jwtFor(UUID.randomUUID())))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.user.last_admin");

        verify(userRepository, never()).deleteById(any());
        verify(keycloakUserAdminService, never()).deleteUser(any());
    }

    @Test
    void deleteUser_allowsKeycloakAdminWhenAnotherStampedAdminExists() {
        UUID userId = UUID.randomUUID();
        Fan fan = new Fan();
        fan.setId(userId);
        fan.setRole(UserRole.FAN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(fan));
        when(keycloakUserAdminService.hasClientRole(userId.toString(), UserRole.ADMIN)).thenReturn(true);
        when(keycloakUserAdminService.listUserIdsWithClientRole(UserRole.ADMIN)).thenReturn(List.of(userId.toString()));
        when(userRepository.findIdsByRole(UserRole.ADMIN)).thenReturn(List.of(UUID.randomUUID()));

        userService.deleteUser(userId, jwtFor(UUID.randomUUID()));

        verify(userRepository).deleteById(userId);
    }

    @Test
    void deleteUser_allowsAdminWhenAnotherAdminExists() {
        UUID adminId = UUID.randomUUID();
        Admin admin = new Admin();
        admin.setId(adminId);
        admin.setRole(UserRole.ADMIN);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(keycloakUserAdminService.listUserIdsWithClientRole(UserRole.ADMIN)).thenReturn(List.of(adminId.toString()));
        when(userRepository.findIdsByRole(UserRole.ADMIN)).thenReturn(List.of(adminId, UUID.randomUUID()));

        userService.deleteUser(adminId, jwtFor(UUID.randomUUID()));

        verify(userRepository).deleteById(adminId);
        verify(keycloakUserAdminService).deleteUser(adminId.toString());
    }

    @Test
    void deleteUser_allowsAdminWhenAnotherKeycloakOnlyAdminExists() {
        UUID adminId = UUID.randomUUID();
        UUID otherKeycloakAdminId = UUID.randomUUID();
        Admin admin = new Admin();
        admin.setId(adminId);
        admin.setRole(UserRole.ADMIN);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(keycloakUserAdminService.hasClientRole(adminId.toString(), UserRole.ADMIN)).thenReturn(true);
        when(keycloakUserAdminService.listUserIdsWithClientRole(UserRole.ADMIN))
                .thenReturn(List.of(adminId.toString(), otherKeycloakAdminId.toString()));
        when(userRepository.findIdsByRole(UserRole.ADMIN)).thenReturn(List.of(adminId));

        userService.deleteUser(adminId, jwtFor(UUID.randomUUID()));

        verify(userRepository).deleteById(adminId);
    }

    @Test
    void deleteUser_fallsBackToLocalAdminCountWhenKeycloakRoleListingFails() {
        UUID adminId = UUID.randomUUID();
        Admin admin = new Admin();
        admin.setId(adminId);
        admin.setRole(UserRole.ADMIN);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(keycloakUserAdminService.listUserIdsWithClientRole(UserRole.ADMIN))
                .thenThrow(new RuntimeException("kc down"));
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.deleteUser(adminId, jwtFor(UUID.randomUUID())))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.user.last_admin");

        verify(userRepository, never()).deleteById(any());

        // hasClientRole sits in the same try: an outage there must also fall back,
        // and must not abort deletes of non-admins.
        UUID fanId = UUID.randomUUID();
        Fan fan = new Fan();
        fan.setId(fanId);
        fan.setRole(UserRole.FAN);
        when(userRepository.findById(fanId)).thenReturn(Optional.of(fan));
        when(keycloakUserAdminService.hasClientRole(fanId.toString(), UserRole.ADMIN))
                .thenThrow(new RuntimeException("kc down"));

        userService.deleteUser(fanId, jwtFor(UUID.randomUUID()));

        verify(userRepository).deleteById(fanId);

        UUID missingKcUserId = UUID.randomUUID();
        Fan missingKcUser = new Fan();
        missingKcUser.setId(missingKcUserId);
        missingKcUser.setRole(UserRole.FAN);
        when(userRepository.findById(missingKcUserId)).thenReturn(Optional.of(missingKcUser));
        when(keycloakUserAdminService.hasClientRole(missingKcUserId.toString(), UserRole.ADMIN))
                .thenThrow(new jakarta.ws.rs.NotFoundException("user gone"));

        userService.deleteUser(missingKcUserId, jwtFor(UUID.randomUUID()));

        verify(userRepository).deleteById(missingKcUserId);
    }

    @Test
    void deleteUser_rejectsDeletingOwnAccount() {
        UUID adminId = UUID.randomUUID();
        Admin admin = new Admin();
        admin.setId(adminId);
        admin.setRole(UserRole.ADMIN);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.deleteUser(adminId, jwtFor(adminId)))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.user.self_delete");

        verify(userRepository, never()).countByRole(any());
        verify(userRepository, never()).deleteById(any());
        verify(keycloakUserAdminService, never()).deleteUser(any());
    }

    @Test
    void deleteUser_rejectsLastClubAdminOfAClub() {
        UUID userId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        ClubAdmin clubAdmin = new ClubAdmin();
        clubAdmin.setId(userId);
        clubAdmin.setRole(UserRole.CLUB_ADMIN);
        Club club = new Club();
        club.setId(clubId);
        ClubAdminMembership membership = new ClubAdminMembership();
        membership.setUser(clubAdmin);
        membership.setClub(club);
        when(userRepository.findById(userId)).thenReturn(Optional.of(clubAdmin));
        when(clubAdminMembershipRepository.findByUser_Id(userId)).thenReturn(List.of(membership));
        when(clubAdminMembershipRepository.findByClub_IdForUpdate(clubId)).thenReturn(List.of(membership));

        assertThatThrownBy(() -> userService.deleteUser(userId, jwtFor(UUID.randomUUID())))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.club_admin.last");

        verify(userRepository, never()).deleteById(any());
        verify(keycloakUserAdminService, never()).deleteUser(any());
        verify(teamMessageRepository, never()).deleteBySenderId(any());
    }

    @Test
    void deleteUser_allowsClubAdminWhenAnotherAdminExistsOnThatClub() {
        UUID userId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        ClubAdmin clubAdmin = new ClubAdmin();
        clubAdmin.setId(userId);
        clubAdmin.setRole(UserRole.CLUB_ADMIN);
        Club club = new Club();
        club.setId(clubId);
        ClubAdminMembership membership = new ClubAdminMembership();
        membership.setUser(clubAdmin);
        membership.setClub(club);
        ClubAdminMembership other = new ClubAdminMembership();
        when(userRepository.findById(userId)).thenReturn(Optional.of(clubAdmin));
        when(clubAdminMembershipRepository.findByUser_Id(userId)).thenReturn(List.of(membership));
        when(clubAdminMembershipRepository.findByClub_IdForUpdate(clubId)).thenReturn(List.of(membership, other));

        userService.deleteUser(userId, jwtFor(UUID.randomUUID()));

        verify(clubAdminMembershipRepository).deleteAll(List.of(membership));
        verify(userRepository).deleteById(userId);
        verify(keycloakUserAdminService).deleteUser(userId.toString());
    }

    private static Jwt jwtFor(UUID userId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("sub", userId.toString())
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(3600))
                .build();
    }
}