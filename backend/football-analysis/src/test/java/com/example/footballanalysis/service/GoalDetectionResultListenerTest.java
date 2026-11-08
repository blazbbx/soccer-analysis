package com.example.footballanalysis.service;

import com.example.footballanalysis.model.Goal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GoalDetectionResultListener tesztek")
class GoalDetectionResultListenerTest {

    @Mock
    private SseNotificationService sseService;

    @Mock
    private MatchService matchService;

    private GoalDetectionResultListener listener() {
        return new GoalDetectionResultListener(new ObjectMapper(), sseService, matchService);
    }

    private static Message message(String json) {
        return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    // The exact shape goal_detection_pipeline.py publishes, including a corner the model was unsure about.
    private static String successJson(UUID matchId) {
        return """
                {"matchId": "%s", "status": "SUCCESS", "goals": [
                  {"corners": [
                    {"x": 100, "y": 50, "source": "detected"},
                    {"x": 200, "y": 50, "source": "detected"},
                    {"x": 100, "y": 150, "source": "clamped"},
                    {"x": null, "y": null, "source": "needs_manual_review"}]}]}
                """.formatted(matchId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void success_isStoredAndPushedToTheOpenDialog() {
        UUID matchId = UUID.randomUUID();
        when(matchService.applyGoalDetectionResult(eq(matchId), any())).thenReturn(true);

        listener().handleGoalDetected(message(successJson(matchId)));

        ArgumentCaptor<List<Goal>> stored = ArgumentCaptor.forClass(List.class);
        verify(matchService).applyGoalDetectionResult(eq(matchId), stored.capture());
        assertThat(stored.getValue()).hasSize(1);
        assertThat(stored.getValue().get(0).corners().get(3).x()).isNull();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(sseService).sendEvent(eq(matchId.toString()), eq("GOAL_DETECTED"), payload.capture());
        Map<String, Object> body = (Map<String, Object>) payload.getValue();
        assertThat(body.get("status")).isEqualTo("COMPLETED");
        assertThat((List<?>) body.get("goals")).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void workerError_becomesFailedWithoutGoals_notAMatchError() {
        UUID matchId = UUID.randomUUID();
        when(matchService.markGoalDetectionFailed(eq(matchId), anyString())).thenReturn(true);

        listener().handleGoalDetected(message(
                "{\"matchId\": \"%s\", \"status\": \"ERROR\", \"errorMessage\": \"camera never stable\"}"
                        .formatted(matchId)));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(sseService).sendEvent(eq(matchId.toString()), eq("GOAL_DETECTED"), payload.capture());
        Map<String, Object> body = (Map<String, Object>) payload.getValue();
        assertThat(body.get("status")).isEqualTo("FAILED");
        assertThat((List<?>) body.get("goals")).isEmpty();
        // The terminal "ERROR" SSE event (which fails the whole upload) must not be sent.
        verify(sseService, never()).notifyClient(anyString(), anyString());
    }

    @Test
    void resultAfterConfirmation_isDroppedSilently() {
        UUID matchId = UUID.randomUUID();
        when(matchService.applyGoalDetectionResult(eq(matchId), any())).thenReturn(false);

        listener().handleGoalDetected(message(successJson(matchId)));

        verifyNoInteractions(sseService);
    }

    @Test
    void messageWithoutMatchId_isIgnored() {
        listener().handleGoalDetected(message("{\"status\": \"SUCCESS\", \"goals\": []}"));

        verifyNoInteractions(matchService, sseService);
    }
}
