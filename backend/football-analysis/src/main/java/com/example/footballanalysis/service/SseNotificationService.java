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
import org.springframework.security.oauth2.jwt.Jwt;

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
    private final TeamRepository teamRepository;

    public SseEmitter subscribe(String matchId, Jwt jwt) {
        Match match = matchRepository.findById(UUID.fromString(matchId))
                .orElseThrow(() -> new NotFoundException("error.match.not_found", new Object[]{matchId}, "Match not found: " + matchId));
        //ensureCanAccessMatch(resolveCurrentUser(jwt), match);
        userAccessService.canAccessMatch(resolveCurrentUser(jwt), UUID.fromString(matchId));


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

    private User resolveCurrentUser(Jwt jwt) {
        if (jwt == null) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required to access this resource.");
        }

        return resolveUserBySubject(jwt.getSubject())
                .or(() -> userRepository.findByEmail(resolveEmail(jwt)))
                .orElseThrow(() -> new NotFoundException("error.user.not_found", new Object[]{resolveLookupValue(jwt)}, "User not found for authenticated user: " + resolveLookupValue(jwt)));
    }

    private Optional<User> resolveUserBySubject(String subject) {
        if (subject == null || subject.isBlank()) {
            return Optional.empty();
        }

        try {
            return userRepository.findById(UUID.fromString(subject));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String resolveEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            email = jwt.getClaimAsString("preferred_username");
        }
        return email;
    }

    private String resolveLookupValue(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject != null && !subject.isBlank()) {
            return subject;
        }
        return resolveEmail(jwt);
    }

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
}
