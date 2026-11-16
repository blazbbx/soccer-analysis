package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SseNotificationService team chat broadcast")
class SseNotificationServiceTest {

    @Mock private MatchRepository matchRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserAccessService userAccessService;
    @Mock private TeamRepository teamRepository;

    @InjectMocks
    private SseNotificationService sseNotificationService;

    @Test
    void broadcastToTeam_deliversOnlyToTheOpenTeamChat() {
        UUID teamId = UUID.randomUUID();
        User admin = admin();

        sseNotificationService.subscribeToTeamChat(admin, teamId);
        sseNotificationService.broadcastToTeam(teamId, "TEAM_CHAT_MESSAGE", "hello");
        sseNotificationService.broadcastToTeam(UUID.randomUUID(), "TEAM_CHAT_MESSAGE", "other-team");

        verify(userAccessService).requireTeamChatAccess(admin, teamId);
        verify(userAccessService, never()).hasTeamAccess(any(), any());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void sendTeamHeartbeats_removesDeadConnectionAndKeepsLiveOne() {
        UUID teamId = UUID.randomUUID();
        User admin = admin();
        when(userAccessService.isAdmin(admin)).thenReturn(true);

        sseNotificationService.subscribeToTeamChat(admin, teamId);
        SseEmitter dead = sseNotificationService.subscribeToTeamChat(admin, teamId);
        // A completed emitter throws IllegalStateException on send, like a connection that has gone away.
        dead.complete();

        sseNotificationService.sendTeamHeartbeats();
        assertThat(sseNotificationService.openTeamChatConnections(teamId)).isEqualTo(1);

        sseNotificationService.sendTeamHeartbeats();
        assertThat(sseNotificationService.openTeamChatConnections(teamId)).isEqualTo(1);
        verify(teamRepository, never()).findUserIdsWithChatAccess(any(), any());
    }

    @Test
    void sendTeamHeartbeats_closesRemovedMemberAndKeepsOtherMemberAndAdmin() {
        UUID teamId = UUID.randomUUID();
        User admin = admin();
        User member = parent();
        User removed = parent();
        when(userAccessService.isAdmin(admin)).thenReturn(true);
        when(teamRepository.findUserIdsWithChatAccess(eq(teamId), eq(Set.of(member.getId(), removed.getId()))))
                .thenReturn(List.of(member.getId()));

        sseNotificationService.subscribeToTeamChat(admin, teamId);
        sseNotificationService.subscribeToTeamChat(member, teamId);
        SseEmitter removedEmitter = sseNotificationService.subscribeToTeamChat(removed, teamId);

        sseNotificationService.sendTeamHeartbeats();

        assertThat(sseNotificationService.openTeamChatConnections(teamId)).isEqualTo(2);
        // The removed member's connection got ACCESS_REVOKED and was completed.
        assertThatThrownBy(() -> removedEmitter.send("late")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sendTeamHeartbeats_keepsConnectionsWhenTheAccessCheckFails() {
        UUID teamId = UUID.randomUUID();
        User member = parent();
        when(teamRepository.findUserIdsWithChatAccess(any(), any())).thenThrow(new IllegalStateException("db down"));

        sseNotificationService.subscribeToTeamChat(member, teamId);
        sseNotificationService.sendTeamHeartbeats();

        assertThat(sseNotificationService.openTeamChatConnections(teamId)).isEqualTo(1);
    }

    private static User admin() {
        User admin = TestHats.user(UserRole.ADMIN);
        admin.setId(UUID.randomUUID());
        TestHats.grant(admin, UserRole.ADMIN);
        return admin;
    }

    private static User parent() {
        User parent = TestHats.user(UserRole.PARENT);
        parent.setId(UUID.randomUUID());
        TestHats.grant(parent, UserRole.PARENT);
        return parent;
    }
}
