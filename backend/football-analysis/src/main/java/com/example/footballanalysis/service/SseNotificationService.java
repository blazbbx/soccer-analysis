package com.example.footballanalysis.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseNotificationService {

    // Thread-safe map to store active connections. Key = fileName, Value = SseEmitter
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String matchId) {
        // Set timeout to 1 hour (video ML processing can take time!)
        SseEmitter emitter = new SseEmitter(3600000L);

        emitters.put(matchId, emitter);

        // Cleanup when the connection drops, times out, or finishes
        emitter.onCompletion(() -> emitters.remove(matchId));
        emitter.onTimeout(() -> emitters.remove(matchId));
        emitter.onError((e) -> emitters.remove(matchId));

        try {
            // Send a dummy event to establish the connection immediately
            emitter.send(SseEmitter.event().name("INIT").data("Connected successfully"));
        } catch (IOException e) {
            emitters.remove(matchId);
        }

        return emitter;
    }

    public void notifyClient(String matchId, String status) {
        SseEmitter emitter = emitters.get(matchId);
        if (emitter != null) {
            try {
                // Push the actual MinIO tracking URL to React!
                emitter.send(SseEmitter.event()
                        .name(status)
                        .data(matchId));

                // Close the connection since we are done
                emitter.complete();
            } catch (IOException e) {
                emitters.remove(matchId);
            }
        } else {
            System.out.println("No active SSE connection found for file: " + matchId);
        }
    }
}
