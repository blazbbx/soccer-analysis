package com.example.footballanalysis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SseNotificationService.class);

    // Thread-safe map to store active connections. Key = fileName, Value = SseEmitter
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String matchId) {
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
