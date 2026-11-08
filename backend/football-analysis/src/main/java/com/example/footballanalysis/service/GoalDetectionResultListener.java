package com.example.footballanalysis.service;

import com.example.footballanalysis.config.RabbitMQConfig;
import com.example.footballanalysis.dto.GoalDetectedMessage;
import com.example.footballanalysis.model.Goal;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes the Python goal-detection worker's result ({@code goal.detected} on
 * {@code detection-exchange}) and pushes a non-terminating {@code GOAL_DETECTED} SSE event, so the
 * confirmation dialog can draw the goals on the same defished frame as the field corners.
 *
 * <p>A failed detection is not a failed match: the event then carries {@code status = FAILED} and
 * no goals, and the user can place the goals by hand. The result is also stored on the match, so a
 * user who comes back later (the dialog was closed) sees it too. A result that arrives after the
 * user has already confirmed is dropped by {@link MatchService#applyGoalDetectionResult}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GoalDetectionResultListener {

    static final String SSE_EVENT = "GOAL_DETECTED";

    private final ObjectMapper objectMapper;
    private final SseNotificationService sseService;
    private final MatchService matchService;

    @RabbitListener(queues = RabbitMQConfig.GOAL_DETECTED_QUEUE_NAME)
    public void handleGoalDetected(Message amqpMessage) {
        try {
            String rawJson = new String(amqpMessage.getBody(), StandardCharsets.UTF_8);
            GoalDetectedMessage message = objectMapper.readValue(rawJson, GoalDetectedMessage.class);

            if (message.matchId() == null || message.matchId().isBlank()) {
                log.warn("goal.detected message ignored — missing matchId");
                return;
            }
            UUID matchId = UUID.fromString(message.matchId());

            String status;
            List<Goal> goals;
            boolean stored;
            if ("ERROR".equalsIgnoreCase(message.status())) {
                status = "FAILED";
                goals = List.of();
                stored = matchService.markGoalDetectionFailed(matchId, message.errorMessage());
            } else {
                status = "COMPLETED";
                goals = message.goals() == null ? List.of() : message.goals();
                stored = matchService.applyGoalDetectionResult(matchId, goals);
            }

            if (!stored) {
                return; // already confirmed or gone: nothing to show
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("status", status);
            payload.put("goals", goals);
            sseService.sendEvent(matchId.toString(), SSE_EVENT, payload);
        } catch (Exception ex) {
            log.error("Failed to process goal.detected message", ex);
        }
    }
}
