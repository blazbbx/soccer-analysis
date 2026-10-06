package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.model.db.InviteKind;
import com.example.footballanalysis.model.db.user.Admin;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Fan;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.db.PlayerParent;
import com.example.footballanalysis.repository.PlayerParentRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtraHatServiceTest {

    @Mock private UserAccessService userAccessService;
    @Mock private KeycloakUserAdminService keycloakUserAdminService;
    @Mock private TeamMembershipQuery teamMembershipQuery;
    @Mock private PlayerProfileRepository playerProfileRepository;
    @Mock private PlayerParentRepository playerParentRepository;

    private ExtraHatService extraHatService;

    @BeforeEach
    void setUp() {
        extraHatService = new ExtraHatService(
                userAccessService, keycloakUserAdminService, teamMembershipQuery, playerProfileRepository, playerParentRepository);
        lenient().when(userAccessService.hasHat(any(), any())).thenAnswer(invocation -> {
            var actor = invocation.getArgument(0, com.example.footballanalysis.model.db.user.User.class);
            UserRole role = invocation.getArgument(1);
            return actor != null && actor.getRole() == role;
        });
    }

    @Test
    void clubAdminInviteAllowsAnyExistingAccount() {
        for (var user : java.util.List.of(new Coach(), new Fan(), new Player(), new Parent())) {
            user.setId(UUID.randomUUID());
            extraHatService.requireKindAllowed(user, InviteKind.CLUB_ADMIN);
        }
    }

    @Test
    void platformAdminMayTakeClubAdminHat() {
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        admin.setRole(UserRole.ADMIN);
        extraHatService.requireKindAllowed(admin, InviteKind.CLUB_ADMIN);
    }

    @Test
    void parentMayAlsoBeAPlayer() {
        Parent parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setRole(UserRole.PARENT);
        extraHatService.requireKindAllowed(parent, InviteKind.PLAYER_SELF);
    }

    @Test
    void playerRoleConflictsWhenParentOfChildOnSameTeam() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        when(teamMembershipQuery.isCoachOnTeam(userId, teamId)).thenReturn(false);
        when(teamMembershipQuery.isFanOnTeam(userId, teamId)).thenReturn(false);
        when(playerProfileRepository.existsByLinkedUser_IdAndTeam_Id(userId, teamId)).thenReturn(false);
        when(playerParentRepository.findLinksForParentOnTeam(userId, teamId)).thenReturn(List.of(new PlayerParent()));

        assertThatThrownBy(() -> extraHatService.requireTeamRoleFree(userId, teamId, UserRole.PLAYER))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.team.role_conflict");
    }

    @Test
    void childLoginIsNeverAnExtraHat() {
        Coach coach = new Coach();
        coach.setId(UUID.randomUUID());
        coach.setRole(UserRole.COACH);
        assertThatThrownBy(() -> extraHatService.requireKindAllowed(coach, InviteKind.CHILD_LOGIN))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.hat.child_login");
    }

    @Test
    void ensureHatAddsMissingClientRole() {
        Coach coach = new Coach();
        coach.setId(UUID.randomUUID());
        coach.setRole(UserRole.COACH);

        extraHatService.ensureHat(coach, UserRole.PARENT);

        verify(keycloakUserAdminService).addClientRole(coach.getId().toString(), UserRole.PARENT);
    }

    @Test
    void grantAndRevokeWaitUntilTheTransactionCommits() {
        UUID userId = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();
        try {
            extraHatService.grantHat(userId, UserRole.FAN);
            extraHatService.revokeHat(userId, UserRole.COACH);
            verify(keycloakUserAdminService, never()).addClientRole(any(), any());
            verify(keycloakUserAdminService, never()).removeClientRole(any(), any());
            TransactionSynchronizationUtils.triggerAfterCommit();
            verify(keycloakUserAdminService).addClientRole(userId.toString(), UserRole.FAN);
            verify(keycloakUserAdminService).removeClientRole(userId.toString(), UserRole.COACH);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void grantHatIsSkippedWhenTheTransactionDoesNotCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            extraHatService.grantHat(UUID.randomUUID(), UserRole.PARENT);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        verify(keycloakUserAdminService, never()).addClientRole(any(), any());
    }

    @Test
    void grantHatDoesNotFailTheRequestWhenKeycloakFailsAfterCommit() {
        UUID userId = UUID.randomUUID();
        doThrow(new RuntimeException("kc down"))
                .when(keycloakUserAdminService).addClientRole(userId.toString(), UserRole.FAN);
        TransactionSynchronizationManager.initSynchronization();
        try {
            extraHatService.grantHat(userId, UserRole.FAN);
            assertThatCode(TransactionSynchronizationUtils::triggerAfterCommit).doesNotThrowAnyException();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        verify(keycloakUserAdminService, times(3)).addClientRole(userId.toString(), UserRole.FAN);
    }

    @Test
    void grantHatRetriesUntilKeycloakSucceeds() {
        UUID userId = UUID.randomUUID();
        doThrow(new RuntimeException("kc down"))
                .doNothing()
                .when(keycloakUserAdminService).addClientRole(userId.toString(), UserRole.FAN);

        assertThatCode(() -> extraHatService.grantHat(userId, UserRole.FAN)).doesNotThrowAnyException();

        verify(keycloakUserAdminService, times(2)).addClientRole(userId.toString(), UserRole.FAN);
    }

    @Test
    void revokeHatRetriesUntilKeycloakSucceeds() {
        UUID userId = UUID.randomUUID();
        doThrow(new RuntimeException("kc down"))
                .doNothing()
                .when(keycloakUserAdminService).removeClientRole(userId.toString(), UserRole.COACH);

        assertThatCode(() -> extraHatService.revokeHat(userId, UserRole.COACH)).doesNotThrowAnyException();

        verify(keycloakUserAdminService, times(2)).removeClientRole(userId.toString(), UserRole.COACH);
    }

    @Test
    void ensureHatSkipsWhenAlreadyPresent() {
        Player player = new Player();
        player.setId(UUID.randomUUID());
        player.setRole(UserRole.PLAYER);

        extraHatService.ensureHat(player, UserRole.PLAYER);

        verify(keycloakUserAdminService, never()).addClientRole(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void teamMutexRejectsCoachWhenAlreadyFan() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        when(teamMembershipQuery.isCoachOnTeam(userId, teamId)).thenReturn(false);
        when(teamMembershipQuery.isFanOnTeam(userId, teamId)).thenReturn(true);
        when(playerProfileRepository.existsByLinkedUser_IdAndTeam_Id(userId, teamId)).thenReturn(false);

        assertThatThrownBy(() -> extraHatService.requireTeamRoleFree(userId, teamId, UserRole.COACH))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.team.role_conflict");
    }
}
