package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SseNotificationService team chat broadcast")
class SseNotificationServiceTest {

    @Mock private MatchRepository matchRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserAccessService userAccessService;
    @Mock private TeamRepository teamRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SseNotificationService sseNotificationService;

    @BeforeEach
    void sendOnTheCallingThread() {
        // The sends run at once, so each test sees their outcome without waiting.
        sseNotificationService.teamSendExecutor = Runnable::run;
    }

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
    void broadcastToTeam_serializesTheEventOnceForAllConnections() throws Exception {
        UUID teamId = UUID.randomUUID();
        User admin = admin();

        sseNotificationService.subscribeToTeamChat(admin, teamId);
        sseNotificationService.subscribeToTeamChat(admin, teamId);
        sseNotificationService.subscribeToTeamChat(admin, teamId);
        sseNotificationService.broadcastToTeam(teamId, "TEAM_CHAT_MESSAGE", "hello");

        verify(objectMapper, times(1)).writeValueAsString("hello");
        assertThat(sseNotificationService.openTeamChatConnections(teamId)).isEqualTo(3);
    }

    @Test
    void broadcastToTeamAfterCommit_aClientThatDoesNotReadHoldsUpOnlyItselfAndIsCutOff() throws Exception {
        UUID teamId = UUID.randomUUID();
        BlockingEmitter slow = new BlockingEmitter();
        CountingEmitter fast = new CountingEmitter();
        Deque<SseEmitter> next = new ArrayDeque<>(List.of(slow, fast));
        // The real executor (virtual threads), and emitters standing in for a client that stops reading and one that reads.
        SseNotificationService service = new SseNotificationService(
                matchRepository, userRepository, userAccessService, teamRepository, objectMapper) {
            @Override
            SseEmitter newTeamChatEmitter() {
                return next.poll();
            }
        };
        try {
            service.subscribeToTeamChat(parent(), teamId);
            service.subscribeToTeamChat(parent(), teamId);
            slow.blocking = true;

            // No transaction here, so the after-commit step runs at once. It returns although the slow send is stuck.
            service.broadcastToTeamAfterCommit(teamId, "TEAM_CHAT_MESSAGE", "first");
            assertThat(slow.stuck.await(5, TimeUnit.SECONDS)).isTrue();

            for (int i = 0; i <= SseNotificationService.MAX_PENDING_EVENTS_PER_CONNECTION; i++) {
                service.broadcastToTeamAfterCommit(teamId, "TEAM_CHAT_MESSAGE", "more " + i);
            }

            // The reading member got every event while the other one was stuck.
            int expected = SseNotificationService.MAX_PENDING_EVENTS_PER_CONNECTION + 2;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (fast.events.get() < expected && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat(fast.events.get()).isEqualTo(expected);
            // The stuck one left too much unread and is out; the reading one stays.
            assertThat(service.openTeamChatConnections(teamId)).isEqualTo(1);
        } finally {
            slow.release.countDown();
            service.shutdownTeamSends();
        }
    }

    /** An emitter whose sends, once blocking is on, wait like a write to a client that does not read. */
    private static final class BlockingEmitter extends SseEmitter {
        final CountDownLatch stuck = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        volatile boolean blocking;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (!blocking) {
                super.send(builder);
                return;
            }
            stuck.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** An emitter that only counts the chat events it was sent (not the INIT). */
    private static final class CountingEmitter extends SseEmitter {
        final AtomicInteger events = new AtomicInteger();
        private boolean initSent;

        @Override
        public void send(SseEventBuilder builder) {
            if (!initSent) {
                initSent = true;
                return;
            }
            events.incrementAndGet();
        }
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
