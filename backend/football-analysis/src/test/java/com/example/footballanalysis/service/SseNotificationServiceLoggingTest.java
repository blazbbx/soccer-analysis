package com.example.footballanalysis.service;

import ch.qos.logback.classic.Level;
import com.example.footballanalysis.testsupport.LogCaptureSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SseNotificationService log tesztek")
class SseNotificationServiceLoggingTest {

    @Test
    void notifyClientWithoutConnection_logsWarning() {
        SseNotificationService service = new SseNotificationService();

        try (LogCaptureSession logs = LogCaptureSession.capture(SseNotificationService.class, Level.WARN)) {
            service.notifyClient("match-123", "COMPLETED");

            assertThat(logs.events())
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.WARN);
                        assertThat(event.getFormattedMessage()).isEqualTo("No active SSE connection found for matchId=match-123");
                    });
        }
    }

    @Test
    void subscribeCreatesEmitterAndDoesNotLogWarnings() {
        SseNotificationService service = new SseNotificationService();

        try (LogCaptureSession logs = LogCaptureSession.capture(SseNotificationService.class, Level.WARN)) {
            SseEmitter emitter = service.subscribe("match-123");

            assertThat(emitter).isNotNull();
            assertThat(logs.events()).isEmpty();
        }
    }
}