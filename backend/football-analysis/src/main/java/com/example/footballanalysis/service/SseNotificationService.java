package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Fan;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
// JWT is resolved in controllers; services receive User objects

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class SseNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SseNotificationService.class);

    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final CoachRepository coachRepository;
    private final PlayerRepository playerRepository;
    private final FanRepository fanRepository;
    private final UserAccessService userAccessService;


    // Thread-safe map to store active connections. Key = fileName, Value = SseEmitter
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    // Team-specific emitters: Key = teamId + "_" + userId + "_" + connectionId, Value = SseEmitter
    private final Map<String, SseEmitter> teamEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String matchId, User actor) {
        Match match = matchRepository.findById(UUID.fromString(matchId))
                .orElseThrow(() -> new NotFoundException("error.match.not_found", new Object[]{matchId}, "Match not found: " + matchId));
        //ensureCanAccessMatch(actor, match);
        userAccessService.canAccessMatch(actor, UUID.fromString(matchId));


        // Set timeout to 1 hour (video ML processing can take time!)
        SseEmitter emitter = new SseEmitter(3600000L);

        emitters.put(matchId, emitter);
        log.atInfo()
                .setMessage("SSE client subscribed for matchId={}")
                .addArgument(matchId)
                .addKeyValue("event_type", "SSE_SUBSCRIBED")
                .addKeyValue("match_id", matchId)
                .log();

        // Cleanup when the connection drops, times out, or finishes
        emitter.onCompletion(() -> {
            emitters.remove(matchId);
            log.atDebug()
                    .setMessage("SSE subscription completed for matchId={}")
                    .addArgument(matchId)
                    .addKeyValue("event_type", "SSE_COMPLETED")
                    .addKeyValue("match_id", matchId)
                    .log();
        });
        emitter.onTimeout(() -> {
            emitters.remove(matchId);
            log.atWarn()
                    .setMessage("SSE subscription timed out for matchId={}")
                    .addArgument(matchId)
                    .addKeyValue("event_type", "SSE_TIMEOUT")
                    .addKeyValue("match_id", matchId)
                    .log();
        });
        emitter.onError(e -> {
            emitters.remove(matchId);
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
            emitters.remove(matchId);
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

    private void ensureCanAccessMatch(User actor, Match match) {
        if (actor == null || match == null) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required to access this resource.");
        }

        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }

        UUID homeTeamId = match.getHomeTeam() != null ? match.getHomeTeam().getId() : null;
        UUID awayTeamId = match.getAwayTeam() != null ? match.getAwayTeam().getId() : null;

        if (homeTeamId == null && awayTeamId == null) {
            throw new BadRequestException("validation.match.access.denied", new Object[0], "This match is not associated with an accessible team.");
        }
        List<UUID> teamIds = Stream.of(match.getHomeTeam(), match.getAwayTeam())
                .filter(Objects::nonNull)
                .map(Team::getId)
                .filter(Objects::nonNull)
                .toList();
        if (!userAccessService.hasTeamAccess(actor, teamIds)) {
            throw new UnauthorizedException("error.auth.forbidden", new Object[0], "Access denied.");
        }
    }

    private boolean hasTeamAccess(User actor, UUID homeId, UUID awayId) {
        List<UUID> matchTeams = Stream.of(homeId, awayId).filter(Objects::nonNull).toList();
        if (matchTeams.isEmpty()) return false;

        UUID userId = actor.getId();

        // Mindenki a saját "házatáján" ellenőriz
        if (actor instanceof Coach) {
            return coachRepository.existsByIdAndTeams_IdIn(userId, matchTeams);
        }
        if (actor instanceof Player) {
            return playerRepository.existsByIdAndTeams_IdIn(userId, matchTeams);
        }
        if (actor instanceof Fan) {
            return fanRepository.existsByIdAndTeams_IdIn(userId, matchTeams);
        }

        return false;
    }

    // JWT resolution is performed by controllers; service methods receive User actor

    public void notifyClient(String matchId, String status) {
        SseEmitter emitter = emitters.get(matchId);
        if (emitter != null) {
            log.atInfo()
                    .setMessage("Sending SSE notification for matchId={}, status={}")
                    .addArgument(matchId)
                    .addArgument(status)
                    .addKeyValue("event_type", "SSE_NOTIFICATION_SENT")
                    .addKeyValue("match_id", matchId)
                    .addKeyValue("status", status)
                    .log();
            try {
                // Push the actual MinIO tracking URL to React!
                emitter.send(SseEmitter.event()
                        .name(status)
                        .data(matchId));

                // Close the connection since we are done
                emitter.complete();
            } catch (IOException e) {
                emitters.remove(matchId);
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
        } else {
            log.atWarn()
                    .setMessage("No active SSE connection found for matchId={}")
                    .addArgument(matchId)
                    .addKeyValue("event_type", "SSE_CONNECTION_MISSING")
                    .addKeyValue("match_id", matchId)
                    .log();
        }
    }

    /**
     * Sends a non-terminating event on the per-match SSE channel. The emitter is left open so
     * subsequent events (e.g. COMPLETED, ERROR) can be delivered on the same connection.
     */
    public void sendEvent(String matchId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(matchId);
        if (emitter == null) {
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
            emitters.remove(matchId);
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

    /**
     * Szétküld egy tetszőleges eseményt az adott csapat összes online tagjai közül azoknak,
     * akik jelenleg aktív SSE kapcsolattal rendelkeznek.
     *
     * @param teamId    a csapat azonosítója
     * @param eventType az esemény típusa
     * @param data      az esemény adatai (pl. ChatMessageResponse)
     */
    public void broadcastToTeam(UUID teamId, String eventType, Object data) {
        final String teamPrefix = teamId.toString() + "_";
        final int[] successCount = {0};
        final int[] totalCount = {0};

        // Bejárjuk az összes aktív team emitter-t és küldünk azoknak, akik a csapathoz tartoznak
        teamEmitters.forEach((key, emitter) -> {
            if (key.startsWith(teamPrefix)) {
                UUID userId = extractUserIdFromEmitterKey(key);
                if (userId == null) {
                    teamEmitters.remove(key);
                    log.atWarn()
                            .setMessage("Removing malformed team emitter key {}")
                            .addArgument(key)
                            .addKeyValue("event_type", "TEAM_SSE_BROADCAST_BAD_KEY")
                            .addKeyValue("team_id", teamId)
                            .log();
                    return;
                }

                Optional<User> userOptional = userRepository.findById(userId);
                if (userOptional.isEmpty() || !userAccessService.hasTeamAccess(userOptional.get(), Collections.singletonList(teamId))) {
                    teamEmitters.remove(key);
                    log.atDebug()
                            .setMessage("Skipping broadcast event {} for user {} without team access")
                            .addArgument(eventType)
                            .addArgument(userId)
                            .addKeyValue("event_type", "TEAM_SSE_BROADCAST_ACCESS_SKIPPED")
                            .addKeyValue("team_id", teamId)
                            .addKeyValue("broadcast_event", eventType)
                            .log();
                    return;
                }

                totalCount[0]++;
                try {
                    emitter.send(SseEmitter.event()
                            .name(eventType)
                            .data(data));
                    successCount[0]++;
                    log.atDebug()
                            .setMessage("Broadcast event {} sent to team connection {}")
                            .addArgument(eventType)
                            .addArgument(key)
                            .addKeyValue("event_type", "TEAM_SSE_BROADCAST_SENT")
                            .addKeyValue("team_id", teamId)
                            .addKeyValue("broadcast_event", eventType)
                            .log();
                } catch (IOException e) {
                    teamEmitters.remove(key);
                    log.atWarn()
                            .setMessage("Failed to send broadcast event {} to team connection {}")
                            .addArgument(eventType)
                            .addArgument(key)
                            .addKeyValue("event_type", "TEAM_SSE_BROADCAST_FAILED")
                            .addKeyValue("team_id", teamId)
                            .addKeyValue("broadcast_event", eventType)
                            .setCause(e)
                            .log();
                }
            }
        });

        log.atInfo()
                .setMessage("Broadcast event {} to teamId={}: {} successful out of {} total")
                .addArgument(eventType)
                .addArgument(teamId)
                .addArgument(successCount[0])
                .addArgument(totalCount[0])
                .addKeyValue("event_type", "TEAM_SSE_BROADCAST_SUMMARY")
                .addKeyValue("team_id", teamId)
                .addKeyValue("broadcast_event", eventType)
                .addKeyValue("successful", successCount[0])
                .addKeyValue("total", totalCount[0])
                .log();
    }

    /**
     * Team chat SSE subscription endpoint - regisztrálja a felhasználó SSE kapcsolatát az összes elérhető csapatához.
     *
     * @return az SseEmitter objektum
     */
    public SseEmitter subscribeToTeamChat(User user) {
        Set<UUID> teamIds = userAccessService.getAccessibleTeamIds(user);
        String connectionId = UUID.randomUUID().toString();
        Set<String> emitterKeys = new HashSet<>();
        teamIds.forEach(teamId -> emitterKeys.add(buildTeamEmitterKey(teamId, user.getId(), connectionId)));

        SseEmitter emitter = new SseEmitter(3600000L); // 1 hour timeout
        emitterKeys.forEach(key -> teamEmitters.put(key, emitter));

        log.atInfo()
                .setMessage("User {} subscribed to team chat for {} teams")
                .addArgument(user.getId())
                .addArgument(teamIds.size())
                .addKeyValue("event_type", "TEAM_CHAT_SUBSCRIBED")
                .addKeyValue("user_id", user.getId())
                .addKeyValue("team_count", teamIds.size())
                .log();

        // Cleanup when the connection drops, times out, or finishes
        emitter.onCompletion(() -> {
            emitterKeys.forEach(teamEmitters::remove);
            log.atDebug()
                    .setMessage("Team chat subscription completed for userId={}, teamCount={}")
                    .addArgument(user.getId())
                    .addArgument(teamIds.size())
                    .addKeyValue("event_type", "TEAM_CHAT_SUBSCRIPTION_COMPLETED")
                    .addKeyValue("user_id", user.getId())
                    .addKeyValue("team_count", teamIds.size())
                    .log();
        });
        emitter.onTimeout(() -> {
            emitterKeys.forEach(teamEmitters::remove);
            log.atWarn()
                    .setMessage("Team chat subscription timed out for userId={}, teamCount={}")
                    .addArgument(user.getId())
                    .addArgument(teamIds.size())
                    .addKeyValue("event_type", "TEAM_CHAT_SUBSCRIPTION_TIMEOUT")
                    .addKeyValue("user_id", user.getId())
                    .addKeyValue("team_count", teamIds.size())
                    .log();
        });
        emitter.onError(e -> {
            emitterKeys.forEach(teamEmitters::remove);
            log.atWarn()
                    .setMessage("Team chat subscription error for userId={}, teamCount={}")
                    .addArgument(user.getId())
                    .addArgument(teamIds.size())
                    .addKeyValue("event_type", "TEAM_CHAT_SUBSCRIPTION_ERROR")
                    .addKeyValue("user_id", user.getId())
                    .addKeyValue("team_count", teamIds.size())
                    .setCause(e)
                    .log();
        });

        try {
            // Send a handshake event to establish the connection
            emitter.send(SseEmitter.event().name("INIT").data("Connected to team chats: " + teamIds.size()));
            log.atDebug()
                    .setMessage("Team chat handshake completed for userId={}, teamCount={}")
                    .addArgument(user.getId())
                    .addArgument(teamIds.size())
                    .addKeyValue("event_type", "TEAM_CHAT_HANDSHAKE_COMPLETED")
                    .addKeyValue("user_id", user.getId())
                    .addKeyValue("team_count", teamIds.size())
                    .log();
        } catch (IOException e) {
            emitterKeys.forEach(teamEmitters::remove);
            log.atError()
                    .setMessage("Failed to initialize team chat subscription for userId={}, teamCount={}")
                    .addArgument(user.getId())
                    .addArgument(teamIds.size())
                    .addKeyValue("event_type", "TEAM_CHAT_HANDSHAKE_FAILED")
                    .addKeyValue("user_id", user.getId())
                    .addKeyValue("team_count", teamIds.size())
                    .setCause(e)
                    .log();
            throw new RuntimeException("Failed to establish SSE connection", e);
        }

        return emitter;
    }

    private String buildTeamEmitterKey(UUID teamId, UUID userId, String connectionId) {
        return teamId + "_" + userId + "_" + connectionId;
    }

    private UUID extractUserIdFromEmitterKey(String key) {
        String[] parts = key.split("_");
        if (parts.length < 2) {
            return null;
        }
        try {
            return UUID.fromString(parts[1]);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
