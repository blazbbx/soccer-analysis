package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class SseNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SseNotificationService.class);

    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final UserAccessService userAccessService;
    private final TeamRepository teamRepository;
    private final ObjectMapper objectMapper;

    /**
     * Team chat events one connection may have waiting. A client that leaves this many unread is cut off: it
     * reconnects and reloads the chat. Until then its events wait in its own outbox, not in anyone else's way.
     */
    static final int MAX_PENDING_EVENTS_PER_CONNECTION = 100;

    /**
     * Runs the sends of the team chat connections. A send blocks while the client does not read, so each
     * connection has its own outbox, drained by one task at a time, and a stuck client holds up only its own
     * (virtual) thread: not the sender's request, the heartbeat, or the other members. Tests swap in a direct executor.
     */
    Executor teamSendExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @PreDestroy
    void shutdownTeamSends() {
        if (teamSendExecutor instanceof ExecutorService executorService) {
            executorService.shutdown();
        }
    }

    /** An open team chat connection and whose it is. platformAdmin comes from the subscriber's own token. */
    record TeamChatConnection(UUID userId, boolean platformAdmin, SseEmitter emitter, Outbox outbox) {
        TeamChatConnection(UUID userId, boolean platformAdmin, SseEmitter emitter) {
            this(userId, platformAdmin, emitter, new Outbox());
        }
    }

    /** The events waiting for one connection, sent in order by at most one task at a time. */
    static final class Outbox {
        private final Queue<PendingEvent> events = new ConcurrentLinkedQueue<>();
        private final AtomicInteger size = new AtomicInteger();
        private final AtomicBoolean draining = new AtomicBoolean();
        private volatile boolean closed;
    }

    /** completeAfter: close the connection once this event went out (the access revoked notice). */
    private record PendingEvent(SseEmitter.SseEventBuilder event, boolean completeAfter) {}

    static final String ACCESS_REVOKED = "ACCESS_REVOKED";

    // Thread-safe map to store active connections. Key = matchId, Value = all live emitters
    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    // Open team-chat connections. Key = teamId. Only the currently opened chat is subscribed.
    private final Map<UUID, Set<TeamChatConnection>> teamEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String matchId, User actor) {
        Match match = matchRepository.findById(UUID.fromString(matchId))
                .orElseThrow(() -> new NotFoundException("error.match.not_found", new Object[]{matchId}, "Match not found: " + matchId));
        userAccessService.requireMatchAccess(actor, match.getId());

        // Set timeout to 1 hour (video ML processing can take time!)
        SseEmitter emitter = new SseEmitter(3600000L);

        emitters.computeIfAbsent(matchId, key -> ConcurrentHashMap.newKeySet()).add(emitter);
        log.atInfo()
                .setMessage("SSE client subscribed for matchId={}")
                .addArgument(matchId)
                .addKeyValue("event_type", "SSE_SUBSCRIBED")
                .addKeyValue("match_id", matchId)
                .log();

        Runnable cleanup = () -> removeMatchEmitter(matchId, emitter);
        emitter.onCompletion(() -> {
            cleanup.run();
            log.atDebug()
                    .setMessage("SSE subscription completed for matchId={}")
                    .addArgument(matchId)
                    .addKeyValue("event_type", "SSE_COMPLETED")
                    .addKeyValue("match_id", matchId)
                    .log();
        });
        emitter.onTimeout(() -> {
            cleanup.run();
            log.atWarn()
                    .setMessage("SSE subscription timed out for matchId={}")
                    .addArgument(matchId)
                    .addKeyValue("event_type", "SSE_TIMEOUT")
                    .addKeyValue("match_id", matchId)
                    .log();
        });
        emitter.onError(e -> {
            cleanup.run();
            log.atWarn()
                    .setMessage("SSE subscription error for matchId={}")
                    .addArgument(matchId)
                    .addKeyValue("event_type", "SSE_ERROR")
                    .addKeyValue("match_id", matchId)
                    .setCause(e)
                    .log();
        });

        try {
            // Send a dummy event to establish the connection immediately
            emitter.send(SseEmitter.event().name("INIT").data("Connected successfully"));
            log.atDebug()
                    .setMessage("SSE handshake completed for matchId={}")
                    .addArgument(matchId)
                    .addKeyValue("event_type", "SSE_HANDSHAKE_COMPLETED")
                    .addKeyValue("match_id", matchId)
                    .log();
        } catch (IOException e) {
            removeMatchEmitter(matchId, emitter);
            log.atError()
                    .setMessage("Failed to initialize SSE subscription for matchId={}")
                    .addArgument(matchId)
                    .addKeyValue("event_type", "SSE_HANDSHAKE_FAILED")
                    .addKeyValue("match_id", matchId)
                    .setCause(e)
                    .log();
        }

        return emitter;
    }

    private void removeMatchEmitter(String matchId, SseEmitter emitter) {
        Set<SseEmitter> live = emitters.get(matchId);
        if (live == null) {
            return;
        }
        live.remove(emitter);
        if (live.isEmpty()) {
            emitters.remove(matchId, live);
        }
    }

    public void notifyClientAfterCommit(String matchId, String status) {
        runAfterCommit(() -> notifyClient(matchId, status));
    }

    public void sendEventAfterCommit(String matchId, String eventName, Object data) {
        runAfterCommit(() -> sendEvent(matchId, eventName, data));
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    public void notifyClient(String matchId, String status) {
        Set<SseEmitter> live = emitters.get(matchId);
        if (live == null || live.isEmpty()) {
            log.atWarn()
                    .setMessage("No active SSE connection found for matchId={}")
                    .addArgument(matchId)
                    .addKeyValue("event_type", "SSE_CONNECTION_MISSING")
                    .addKeyValue("match_id", matchId)
                    .log();
            return;
        }

        log.atInfo()
                .setMessage("Sending SSE notification for matchId={}, status={}, subscribers={}")
                .addArgument(matchId)
                .addArgument(status)
                .addArgument(live.size())
                .addKeyValue("event_type", "SSE_NOTIFICATION_SENT")
                .addKeyValue("match_id", matchId)
                .addKeyValue("status", status)
                .log();

        for (SseEmitter emitter : live) {
            try {
                emitter.send(SseEmitter.event()
                        .name(status)
                        .data(matchId));
                emitter.complete();
            } catch (IOException | IllegalStateException e) {
                removeMatchEmitter(matchId, emitter);
                log.atError()
                        .setMessage("Failed to send SSE notification for matchId={}, status={}")
                        .addArgument(matchId)
                        .addArgument(status)
                        .addKeyValue("event_type", "SSE_NOTIFICATION_FAILED")
                        .addKeyValue("match_id", matchId)
                        .addKeyValue("status", status)
                        .setCause(e)
                        .log();
            }
        }
        emitters.remove(matchId);
    }

    /**
     * Sends a non-terminating event on the per-match SSE channel. The emitter is left open so
     * subsequent events (e.g. COMPLETED, ERROR) can be delivered on the same connection.
     */
    public void sendEvent(String matchId, String eventName, Object data) {
        Set<SseEmitter> live = emitters.get(matchId);
        if (live == null || live.isEmpty()) {
            log.atWarn()
                    .setMessage("No active SSE connection for matchId={}; dropping event {}")
                    .addArgument(matchId)
                    .addArgument(eventName)
                    .addKeyValue("event_type", "SSE_EVENT_DROPPED")
                    .addKeyValue("match_id", matchId)
                    .addKeyValue("sse_event", eventName)
                    .log();
            return;
        }

        for (SseEmitter emitter : live) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
                log.atInfo()
                        .setMessage("Sent SSE event {} for matchId={}")
                        .addArgument(eventName)
                        .addArgument(matchId)
                        .addKeyValue("event_type", "SSE_EVENT_SENT")
                        .addKeyValue("match_id", matchId)
                        .addKeyValue("sse_event", eventName)
                        .log();
            } catch (IOException | IllegalStateException e) {
                removeMatchEmitter(matchId, emitter);
                log.atError()
                        .setMessage("Failed to send SSE event {} for matchId={}")
                        .addArgument(eventName)
                        .addArgument(matchId)
                        .addKeyValue("event_type", "SSE_EVENT_FAILED")
                        .addKeyValue("match_id", matchId)
                        .addKeyValue("sse_event", eventName)
                        .setCause(e)
                        .log();
            }
        }
    }

    /** After the commit, puts the event in the outbox of each open connection; the caller does not wait for the sends. */
    public void broadcastToTeamAfterCommit(UUID teamId, String eventType, Object data) {
        runAfterCommit(() -> broadcastToTeam(teamId, eventType, data));
    }

    /**
     * Szétküld egy tetszőleges eseményt az adott csapat összes online tagjai közül azoknak,
     * akik jelenleg aktív SSE kapcsolattal rendelkeznek. Csak sorba teszi kapcsolatonként, nem vár a küldésre.
     *
     * @param teamId    a csapat azonosítója
     * @param eventType az esemény típusa
     * @param data      az esemény adatai (pl. ChatMessageResponse)
     */
    public void broadcastToTeam(UUID teamId, String eventType, Object data) {
        Set<TeamChatConnection> live = teamEmitters.get(teamId);
        if (live == null || live.isEmpty()) {
            log.atDebug()
                    .setMessage("No open team chat for teamId={}; dropping event {}")
                    .addArgument(teamId)
                    .addArgument(eventType)
                    .addKeyValue("event_type", "TEAM_SSE_BROADCAST_DROPPED")
                    .addKeyValue("team_id", teamId)
                    .addKeyValue("broadcast_event", eventType)
                    .log();
            return;
        }

        // Serialized once, not per connection; a String is written as is by the String converter.
        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.atError()
                    .setMessage("Could not serialize event {} for teamId={}")
                    .addArgument(eventType)
                    .addArgument(teamId)
                    .addKeyValue("event_type", "TEAM_SSE_BROADCAST_SERIALIZATION_FAILED")
                    .addKeyValue("team_id", teamId)
                    .addKeyValue("broadcast_event", eventType)
                    .setCause(e)
                    .log();
            return;
        }

        int total = live.size();
        int queued = 0;
        for (TeamChatConnection connection : live) {
            // A builder per connection: building one appends to it, so it cannot be sent twice.
            if (enqueue(teamId, connection, SseEmitter.event().name(eventType).data(json, MediaType.APPLICATION_JSON), false)) {
                queued++;
            }
        }

        log.atInfo()
                .setMessage("Broadcast event {} to teamId={}: queued for {} out of {} connections")
                .addArgument(eventType)
                .addArgument(teamId)
                .addArgument(queued)
                .addArgument(total)
                .addKeyValue("event_type", "TEAM_SSE_BROADCAST_SUMMARY")
                .addKeyValue("team_id", teamId)
                .addKeyValue("broadcast_event", eventType)
                .addKeyValue("queued", queued)
                .addKeyValue("total", total)
                .log();
    }

    /**
     * Puts an event in the connection's outbox and makes sure a task is sending it. False when the connection
     * is already closed, or was just cut off for leaving {@link #MAX_PENDING_EVENTS_PER_CONNECTION} events unread.
     */
    private boolean enqueue(UUID teamId, TeamChatConnection connection, SseEmitter.SseEventBuilder event, boolean completeAfter) {
        Outbox outbox = connection.outbox();
        if (outbox.closed) {
            return false;
        }
        if (outbox.size.incrementAndGet() > MAX_PENDING_EVENTS_PER_CONNECTION) {
            outbox.size.decrementAndGet();
            dropSlowConnection(teamId, connection);
            return false;
        }
        outbox.events.add(new PendingEvent(event, completeAfter));
        startDrain(teamId, connection);
        return true;
    }

    private void startDrain(UUID teamId, TeamChatConnection connection) {
        Outbox outbox = connection.outbox();
        if (!outbox.draining.compareAndSet(false, true)) {
            return;
        }
        try {
            teamSendExecutor.execute(() -> drain(teamId, connection));
        } catch (RejectedExecutionException e) {
            // Shutting down: the connections go with the server.
            outbox.draining.set(false);
        }
    }

    /** Sends the outbox in order. Only one drain runs per connection, so a blocked send holds up only this one. */
    private void drain(UUID teamId, TeamChatConnection connection) {
        Outbox outbox = connection.outbox();
        while (true) {
            PendingEvent next;
            while ((next = outbox.events.poll()) != null) {
                outbox.size.decrementAndGet();
                if (outbox.closed) {
                    continue;
                }
                try {
                    connection.emitter().send(next.event());
                    if (next.completeAfter()) {
                        outbox.closed = true;
                        connection.emitter().complete();
                    }
                } catch (IOException | IllegalStateException e) {
                    outbox.closed = true;
                    removeTeamEmitter(teamId, connection);
                    log.atDebug()
                            .setMessage("Team chat send failed for userId={}, teamId={}; connection removed")
                            .addArgument(connection.userId())
                            .addArgument(teamId)
                            .addKeyValue("event_type", "TEAM_SSE_SEND_FAILED")
                            .addKeyValue("user_id", connection.userId())
                            .addKeyValue("team_id", teamId)
                            .setCause(e)
                            .log();
                }
            }
            outbox.draining.set(false);
            // An event added after the last poll but before the flag was cleared found the drain still running and
            // started none: take it over here, unless a new drain already did.
            if (outbox.events.isEmpty() || !outbox.draining.compareAndSet(false, true)) {
                return;
            }
        }
    }

    /** A client that does not read: out of the map now, its events dropped; it reconnects and reloads the chat. */
    private void dropSlowConnection(UUID teamId, TeamChatConnection connection) {
        Outbox outbox = connection.outbox();
        outbox.closed = true;
        outbox.events.clear();
        removeTeamEmitter(teamId, connection);
        log.atWarn()
                .setMessage("Cut off team chat of userId={} for teamId={}: {} events left unread")
                .addArgument(connection.userId())
                .addArgument(teamId)
                .addArgument(MAX_PENDING_EVENTS_PER_CONNECTION)
                .addKeyValue("event_type", "TEAM_CHAT_SLOW_CONNECTION_DROPPED")
                .addKeyValue("user_id", connection.userId())
                .addKeyValue("team_id", teamId)
                .log();
        // complete() waits for the send that is stuck on this connection, so it runs on its own thread.
        try {
            teamSendExecutor.execute(() -> {
                try {
                    connection.emitter().complete();
                } catch (RuntimeException e) {
                    // Already gone.
                }
            });
        } catch (RejectedExecutionException e) {
            // Shutting down: the connection goes with the server.
        }
    }

    /**
     * SSE a megnyitott csapat chatjéhez. Jogosultságot itt ellenőrizzük;
     * a broadcast már csak a feliratkozott kapcsolatoknak küld.
     */
    public SseEmitter subscribeToTeamChat(User user, UUID teamId) {
        userAccessService.requireTeamChatAccess(user, teamId);

        SseEmitter emitter = newTeamChatEmitter();
        TeamChatConnection connection = new TeamChatConnection(user.getId(), userAccessService.isAdmin(user), emitter);

        Runnable cleanup = () -> {
            connection.outbox().closed = true;
            removeTeamEmitter(teamId, connection);
        };
        emitter.onCompletion(() -> {
            cleanup.run();
            log.atDebug()
                    .setMessage("Team chat subscription completed for userId={}, teamId={}")
                    .addArgument(user.getId())
                    .addArgument(teamId)
                    .addKeyValue("event_type", "TEAM_CHAT_SUBSCRIPTION_COMPLETED")
                    .addKeyValue("user_id", user.getId())
                    .addKeyValue("team_id", teamId)
                    .log();
        });
        emitter.onTimeout(() -> {
            cleanup.run();
            log.atWarn()
                    .setMessage("Team chat subscription timed out for userId={}, teamId={}")
                    .addArgument(user.getId())
                    .addArgument(teamId)
                    .addKeyValue("event_type", "TEAM_CHAT_SUBSCRIPTION_TIMEOUT")
                    .addKeyValue("user_id", user.getId())
                    .addKeyValue("team_id", teamId)
                    .log();
        });
        emitter.onError(e -> {
            cleanup.run();
            log.atWarn()
                    .setMessage("Team chat subscription error for userId={}, teamId={}")
                    .addArgument(user.getId())
                    .addArgument(teamId)
                    .addKeyValue("event_type", "TEAM_CHAT_SUBSCRIPTION_ERROR")
                    .addKeyValue("user_id", user.getId())
                    .addKeyValue("team_id", teamId)
                    .setCause(e)
                    .log();
        });

        // INIT goes out before the connection is in the map, so no broadcast can come before it. The response is not
        // started yet, so the emitter only holds it until then and this send does not block.
        try {
            emitter.send(SseEmitter.event().name("INIT").data("Connected to team chat: " + teamId));
        } catch (IOException e) {
            log.atError()
                    .setMessage("Failed to initialize team chat subscription for userId={}, teamId={}")
                    .addArgument(user.getId())
                    .addArgument(teamId)
                    .addKeyValue("event_type", "TEAM_CHAT_HANDSHAKE_FAILED")
                    .addKeyValue("user_id", user.getId())
                    .addKeyValue("team_id", teamId)
                    .setCause(e)
                    .log();
            throw new RuntimeException("Failed to establish SSE connection", e);
        }

        teamEmitters.computeIfAbsent(teamId, key -> ConcurrentHashMap.newKeySet()).add(connection);
        log.atInfo()
                .setMessage("User {} subscribed to team chat teamId={}")
                .addArgument(user.getId())
                .addArgument(teamId)
                .addKeyValue("event_type", "TEAM_CHAT_SUBSCRIBED")
                .addKeyValue("user_id", user.getId())
                .addKeyValue("team_id", teamId)
                .log();

        return emitter;
    }

    /** A new team chat connection, open for an hour. Tests override it to get an emitter whose sends block. */
    SseEmitter newTeamChatEmitter() {
        return new SseEmitter(3600000L);
    }

    /**
     * Every 25 s: closes the connections of users who lost access to the team (one database query
     * per team, admins skipped), then queues an SSE comment ({@code : ping}) on the rest, so proxies
     * do not cut idle connections and the client watchdog sees data. A connection whose send fails is
     * removed by its outbox; one that left too much unread is cut off. Nothing here waits for a client.
     */
    @Scheduled(fixedDelayString = "${app.chat.heartbeat-ms:25000}")
    public void sendTeamHeartbeats() {
        int queued = 0;
        int dropped = 0;
        int revoked = 0;
        for (Map.Entry<UUID, Set<TeamChatConnection>> entry : teamEmitters.entrySet()) {
            UUID teamId = entry.getKey();
            Set<UUID> allowed = usersWithChatAccess(teamId, entry.getValue());
            for (TeamChatConnection connection : entry.getValue()) {
                if (allowed != null && !connection.platformAdmin() && !allowed.contains(connection.userId())) {
                    revoke(teamId, connection);
                    revoked++;
                    continue;
                }
                if (enqueue(teamId, connection, SseEmitter.event().comment("ping"), false)) {
                    queued++;
                } else {
                    dropped++;
                }
            }
        }
        if (queued > 0 || dropped > 0 || revoked > 0) {
            log.atDebug()
                    .setMessage("Team chat heartbeat: {} queued, {} closed or cut off, {} revoked")
                    .addArgument(queued)
                    .addArgument(dropped)
                    .addArgument(revoked)
                    .addKeyValue("event_type", "TEAM_CHAT_HEARTBEAT")
                    .addKeyValue("queued", queued)
                    .addKeyValue("dropped", dropped)
                    .addKeyValue("revoked", revoked)
                    .log();
        }
    }

    /** Users of these connections who may still read the chat, or null when the check failed (then nothing is closed). */
    private Set<UUID> usersWithChatAccess(UUID teamId, Set<TeamChatConnection> connections) {
        Set<UUID> userIds = new HashSet<>();
        for (TeamChatConnection connection : connections) {
            if (!connection.platformAdmin()) {
                userIds.add(connection.userId());
            }
        }
        if (userIds.isEmpty()) {
            return Set.of();
        }
        try {
            return new HashSet<>(teamRepository.findUserIdsWithChatAccess(teamId, userIds));
        } catch (RuntimeException e) {
            log.atWarn()
                    .setMessage("Team chat access check failed for teamId={}")
                    .addArgument(teamId)
                    .addKeyValue("event_type", "TEAM_CHAT_ACCESS_CHECK_FAILED")
                    .addKeyValue("team_id", teamId)
                    .setCause(e)
                    .log();
            return null;
        }
    }

    private void revoke(UUID teamId, TeamChatConnection connection) {
        removeTeamEmitter(teamId, connection);
        // Out of the map now, so no new chat event reaches it; the notice goes after what is already queued, then it closes.
        enqueue(teamId, connection, SseEmitter.event().name(ACCESS_REVOKED).data(teamId.toString()), true);
        log.atInfo()
                .setMessage("Closed team chat of userId={} for teamId={}: no access any more")
                .addArgument(connection.userId())
                .addArgument(teamId)
                .addKeyValue("event_type", "TEAM_CHAT_ACCESS_REVOKED")
                .addKeyValue("user_id", connection.userId())
                .addKeyValue("team_id", teamId)
                .log();
    }

    /** Number of open chat connections of a team. For tests. */
    int openTeamChatConnections(UUID teamId) {
        Set<TeamChatConnection> live = teamEmitters.get(teamId);
        return live == null ? 0 : live.size();
    }

    private void removeTeamEmitter(UUID teamId, TeamChatConnection connection) {
        Set<TeamChatConnection> live = teamEmitters.get(teamId);
        if (live == null) {
            return;
        }
        live.remove(connection);
        if (live.isEmpty()) {
            teamEmitters.remove(teamId, live);
        }
    }
}
