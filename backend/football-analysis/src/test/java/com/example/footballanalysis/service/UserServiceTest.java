package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.User;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.ClubAdminMembership;
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

    @Mock
    private PlatformAdminDirectory platformAdminDirectory;

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
        User first = fan(UUID.randomUUID());
        first.setLastName("Alma");
        User second = fan(UUID.randomUUID());
        second.setLastName("Beke");
        User third = fan(UUID.randomUUID());
        third.setLastName("Ceres");
        List<User> all = List.of(first, second, third);
        when(userRepository.findPage(any())).thenAnswer(invocation -> {
            org.springframework.data.domain.Pageable pageable = invocation.getArgument(0);
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), all.size());
            List<User> slice = start >= all.size() ? List.of() : all.subList(start, end);
            return new org.springframework.data.domain.PageImpl<>(slice, pageable, all.size());
        });
        when(platformAdminDirectory.adminIdsForDisplay())
                .thenReturn(java.util.Set.of(UUID.randomUUID(), UUID.randomUUID()));

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

        var page = userService.getAllUsers(1, 20, " Kov ");

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
        verify(userRepository, never()).findPage(any());
    }

    @Test
    void updateUser_updatesUserInDbAndKeycloak() {
        UUID userId = UUID.randomUUID();

        User user = TestHats.user(UserRole.ADMIN);
        user.setId(userId);
        user.setEmail("admin@test.com");
        user.setFirstName("Old");
        user.setLastName("Name");
        TestHats.grant(user, UserRole.ADMIN);
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

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
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
        User user = TestHats.user(UserRole.ADMIN);
        user.setId(userId);
        user.setEmail("admin@test.com");
        user.setFirstName("Old");
        user.setLastName("Name");
        TestHats.grant(user, UserRole.ADMIN);
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
    void updateUser_copiesNewNameToLinkedProfile() {
        UUID userId = UUID.randomUUID();
        User user = TestHats.user(UserRole.PLAYER);
        user.setId(userId);
        user.setEmail("player@test.com");
        user.setFirstName("Old");
        user.setLastName("Name");
        user.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        PlayerProfile profile = new PlayerProfile();
        profile.setFirstName("Old");
        profile.setLastName("Name");
        profile.setDateOfBirth(LocalDate.of(2010, 5, 6));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerProfileRepository.findByLinkedUser_Id(userId)).thenReturn(Optional.of(profile));

        userService.updateMyUser(new UpdateUserRequest(" New ", "Surname"), jwtFor(userId));

        assertThat(profile.getFirstName()).isEqualTo("New");
        assertThat(profile.getLastName()).isEqualTo("Surname");
        assertThat(profile.getDateOfBirth()).isEqualTo(LocalDate.of(2010, 5, 6));
        verify(playerProfileRepository).save(profile);
    }

    @Test
    void updateUser_withoutLinkedProfileSavesNoProfile() {
        UUID userId = UUID.randomUUID();
        User user = TestHats.user(UserRole.COACH);
        user.setId(userId);
        user.setEmail("coach@test.com");
        user.setFirstName("Old");
        user.setLastName("Name");
        user.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerProfileRepository.findByLinkedUser_Id(userId)).thenReturn(Optional.empty());

        userService.updateMyUser(new UpdateUserRequest("New", "Surname"), jwtFor(userId));

        verify(playerProfileRepository, never()).save(any());
    }

    @Test
    void updateMyUser_usesJwtSubjectAsCurrentUserId() {
        UUID userId = UUID.randomUUID();

        User user = TestHats.user(UserRole.ADMIN);
        user.setId(userId);
        user.setEmail("admin@test.com");
        user.setFirstName("Old");
        user.setLastName("Name");
        TestHats.grant(user, UserRole.ADMIN);
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
        User actor = TestHats.user(UserRole.CLUB_ADMIN);
        actor.setId(actorId);
        TestHats.grant(actor, UserRole.CLUB_ADMIN);
        User target = TestHats.user(UserRole.FAN);
        target.setId(targetId);
        TestHats.grant(target, UserRole.FAN);

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
        User actor = clubAdmin(actorId);
        User target = fan(targetId);
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
        User actor = clubAdmin(actorId);
        User target = fan(targetId);
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
    void updateUser_clubAdminCannotEditAnyoneWhileKeycloakCannotBeAsked() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        User actor = clubAdmin(actorId);
        // A plain member in the database: during an outage it still counts as a possible platform admin.
        User target = fan(targetId);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userAccessService.hasHat(actor, UserRole.CLUB_ADMIN)).thenReturn(true);
        when(keycloakUserAdminService.hasClientRole(targetId.toString(), UserRole.ADMIN))
                .thenThrow(new RuntimeException("kc down"));
        shareClub(actor, target, clubId);

        assertThatThrownBy(() -> userService.updateUser(targetId, new UpdateUserRequest("A", "B"), jwtFor(actorId)))
                .isInstanceOf(ForbiddenException.class);

        verify(keycloakUserAdminService, never()).updateUser(any(), any(), any());
    }

    private static User clubAdmin(UUID actorId) {
        User actor = TestHats.user(UserRole.CLUB_ADMIN);
        actor.setId(actorId);
        TestHats.grant(actor, UserRole.CLUB_ADMIN);
        return actor;
    }

    private static User fan(UUID targetId) {
        User target = TestHats.user(UserRole.FAN);
        target.setId(targetId);
        TestHats.grant(target, UserRole.FAN);
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

        User fan = TestHats.user(UserRole.FAN);
        fan.setId(userId);
        TestHats.grant(fan, UserRole.FAN);
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
        User admin = TestHats.user(UserRole.ADMIN);
        admin.setId(adminId);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(keycloakUserAdminService.hasClientRole(adminId.toString(), UserRole.ADMIN)).thenReturn(true);
        when(keycloakUserAdminService.listUserIdsWithClientRole(UserRole.ADMIN)).thenReturn(List.of(adminId.toString()));

        assertThatThrownBy(() -> userService.deleteUser(adminId, jwtFor(UUID.randomUUID())))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.user.last_admin");

        verify(userRepository, never()).deleteById(any());
        verify(keycloakUserAdminService, never()).deleteUser(any());
        verify(teamMessageRepository, never()).deleteBySenderId(any());
    }

    @Test
    void deleteUser_onlyKeycloakDecidesWhoIsAnAdmin() {
        // FAN in the database, admin in Keycloak, and the only one: refused.
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(fan(userId)));
        when(keycloakUserAdminService.hasClientRole(userId.toString(), UserRole.ADMIN)).thenReturn(true);
        when(keycloakUserAdminService.listUserIdsWithClientRole(UserRole.ADMIN)).thenReturn(List.of(userId.toString()));

        assertThatThrownBy(() -> userService.deleteUser(userId, jwtFor(UUID.randomUUID())))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.user.last_admin");

        // ADMIN in the database, but not in Keycloak: an ordinary account, it can go.
        UUID formerAdminId = UUID.randomUUID();
        User formerAdmin = TestHats.user(UserRole.ADMIN);
        formerAdmin.setId(formerAdminId);
        when(userRepository.findById(formerAdminId)).thenReturn(Optional.of(formerAdmin));
        when(keycloakUserAdminService.hasClientRole(formerAdminId.toString(), UserRole.ADMIN)).thenReturn(false);

        userService.deleteUser(formerAdminId, jwtFor(UUID.randomUUID()));

        verify(userRepository).deleteById(formerAdminId);
        verify(userRepository, never()).deleteById(userId);
    }

    @Test
    void deleteUser_allowsAdminWhenAnotherAdminExists() {
        UUID adminId = UUID.randomUUID();
        UUID otherAdminId = UUID.randomUUID();
        User admin = TestHats.user(UserRole.ADMIN);
        admin.setId(adminId);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(keycloakUserAdminService.hasClientRole(adminId.toString(), UserRole.ADMIN)).thenReturn(true);
        when(keycloakUserAdminService.listUserIdsWithClientRole(UserRole.ADMIN))
                .thenReturn(List.of(adminId.toString(), otherAdminId.toString()));

        userService.deleteUser(adminId, jwtFor(UUID.randomUUID()));

        verify(userRepository).deleteById(adminId);
        verify(keycloakUserAdminService).deleteUser(adminId.toString());
    }

    @Test
    void deleteUser_refusesWhileKeycloakCannotBeAsked() {
        // The admin check itself fails: nobody can be deleted, an admin could be among them.
        UUID fanId = UUID.randomUUID();
        when(userRepository.findById(fanId)).thenReturn(Optional.of(fan(fanId)));
        when(keycloakUserAdminService.hasClientRole(fanId.toString(), UserRole.ADMIN))
                .thenThrow(new RuntimeException("kc down"));

        assertThatThrownBy(() -> userService.deleteUser(fanId, jwtFor(UUID.randomUUID())))
                .isInstanceOf(com.example.footballanalysis.exception.ExternalServiceException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.keycloak.admin_check_failed");

        // The admin is known, but the list of the others cannot be read.
        UUID adminId = UUID.randomUUID();
        User admin = TestHats.user(UserRole.ADMIN);
        admin.setId(adminId);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(keycloakUserAdminService.hasClientRole(adminId.toString(), UserRole.ADMIN)).thenReturn(true);
        when(keycloakUserAdminService.listUserIdsWithClientRole(UserRole.ADMIN))
                .thenThrow(new RuntimeException("kc down"));

        assertThatThrownBy(() -> userService.deleteUser(adminId, jwtFor(UUID.randomUUID())))
                .isInstanceOf(com.example.footballanalysis.exception.ExternalServiceException.class);

        verify(userRepository, never()).deleteById(any());
        verify(teamMessageRepository, never()).deleteBySenderId(any());
    }

    @Test
    void deleteUser_aUserMissingFromKeycloakIsNotAnAdmin() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(fan(userId)));
        when(keycloakUserAdminService.hasClientRole(userId.toString(), UserRole.ADMIN))
                .thenThrow(new jakarta.ws.rs.NotFoundException("user gone"));

        userService.deleteUser(userId, jwtFor(UUID.randomUUID()));

        verify(userRepository).deleteById(userId);
    }

    @Test
    void deleteUser_rejectsDeletingOwnAccount() {
        UUID adminId = UUID.randomUUID();
        User admin = TestHats.user(UserRole.ADMIN);
        admin.setId(adminId);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.deleteUser(adminId, jwtFor(adminId)))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.user.self_delete");

        verify(keycloakUserAdminService, never()).hasClientRole(any(), any());
        verify(userRepository, never()).deleteById(any());
        verify(keycloakUserAdminService, never()).deleteUser(any());
    }

    @Test
    void deleteUser_rejectsLastClubAdminOfAClub() {
        UUID userId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        User clubAdmin = TestHats.user(UserRole.CLUB_ADMIN);
        clubAdmin.setId(userId);
        TestHats.grant(clubAdmin, UserRole.CLUB_ADMIN);
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
        User clubAdmin = TestHats.user(UserRole.CLUB_ADMIN);
        clubAdmin.setId(userId);
        TestHats.grant(clubAdmin, UserRole.CLUB_ADMIN);
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