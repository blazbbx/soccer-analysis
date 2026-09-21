package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class SseNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SseNotificationService.class);

    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final UserAccessService userAccessService;


    // Thread-safe map to store active connections. Key = matchId, Value = all live emitters
    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    // Open team-chat connections. Key = teamId. Only the currently opened chat is subscribed.
    private final Map<UUID, Set<SseEmitter>> teamEmitters = new ConcurrentHashMap<>();

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
            } catch (IOException e) {
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
            } catch (IOException e) {
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

    public void broadcastToTeamAfterCommit(UUID teamId, String eventType, Object data) {
        runAfterCommit(() -> broadcastToTeam(teamId, eventType, data));
    }

    /**
     * Szétküld egy tetszőleges eseményt az adott csapat összes online tagjai közül azoknak,
     * akik jelenleg aktív SSE kapcsolattal rendelkeznek.
     *
     * @param teamId    a csapat azonosítója
     * @param eventType az esemény típusa
     * @param data      az esemény adatai (pl. ChatMessageResponse)
     */
    public void broadcastToTeam(UUID teamId, String eventType, Object data) {
        Set<SseEmitter> live = teamEmitters.get(teamId);
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

        int successCount = 0;
        for (SseEmitter emitter : live) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventType)
                        .data(data, MediaType.APPLICATION_JSON));
                successCount++;
            } catch (IOException e) {
                removeTeamEmitter(teamId, emitter);
                log.atWarn()
                        .setMessage("Failed to send broadcast event {} to teamId={}")
                        .addArgument(eventType)
                        .addArgument(teamId)
                        .addKeyValue("event_type", "TEAM_SSE_BROADCAST_FAILED")
                        .addKeyValue("team_id", teamId)
                        .addKeyValue("broadcast_event", eventType)
                        .setCause(e)
                        .log();
            }
        }

        log.atInfo()
                .setMessage("Broadcast event {} to teamId={}: {} successful out of {} total")
                .addArgument(eventType)
                .addArgument(teamId)
                .addArgument(successCount)
                .addArgument(live.size())
                .addKeyValue("event_type", "TEAM_SSE_BROADCAST_SUMMARY")
                .addKeyValue("team_id", teamId)
                .addKeyValue("broadcast_event", eventType)
                .addKeyValue("successful", successCount)
                .addKeyValue("total", live.size())
                .log();
    }

    /**
     * SSE a megnyitott csapat chatjéhez. Jogosultságot itt ellenőrizzük;
     * a broadcast már csak a feliratkozott kapcsolatoknak küld.
     */
    public SseEmitter subscribeToTeamChat(User user, UUID teamId) {
        userAccessService.requireTeamAccess(user, List.of(teamId));

        SseEmitter emitter = new SseEmitter(3600000L);
        teamEmitters.computeIfAbsent(teamId, key -> ConcurrentHashMap.newKeySet()).add(emitter);

        log.atInfo()
                .setMessage("User {} subscribed to team chat teamId={}")
                .addArgument(user.getId())
                .addArgument(teamId)
                .addKeyValue("event_type", "TEAM_CHAT_SUBSCRIBED")
                .addKeyValue("user_id", user.getId())
                .addKeyValue("team_id", teamId)
                .log();

        Runnable cleanup = () -> removeTeamEmitter(teamId, emitter);
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

        try {
            emitter.send(SseEmitter.event().name("INIT").data("Connected to team chat: " + teamId));
        } catch (IOException e) {
            removeTeamEmitter(teamId, emitter);
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

        return emitter;
    }

    private void removeTeamEmitter(UUID teamId, SseEmitter emitter) {
        Set<SseEmitter> live = teamEmitters.get(teamId);
        if (live == null) {
            return;
        }
        live.remove(emitter);
        if (live.isEmpty()) {
            teamEmitters.remove(teamId, live);
        }
    }
}
