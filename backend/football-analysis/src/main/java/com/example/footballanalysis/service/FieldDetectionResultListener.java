package com.example.footballanalysis.service;

import com.example.footballanalysis.config.RabbitMQConfig;
import com.example.footballanalysis.dto.FieldDetectedMessage;
import com.example.footballanalysis.model.Corner;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes results emitted by the Python field-detection worker (`field.detected` on
 * `detection-exchange`) and pushes a non-terminating SSE event so the frontend can show
 * the defisheyed frame plus the predicted corners.
 *
 * The Python worker returns the defished image URL with the docker-internal MinIO host
 * (e.g. http://minio:9000/...) — we rewrite it to the public host so the browser can
 * actually load the image.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FieldDetectionResultListener {

    private final ObjectMapper objectMapper;
    private final SseNotificationService sseService;
    private final MatchService matchService;

    @Value("${minio.endpoint}")
    private String internalEndpoint;

    @Value("${minio.public-endpoint}")
    private String publicEndpoint;

    @RabbitListener(queues = RabbitMQConfig.FIELD_DETECTED_QUEUE_NAME)
    public void handleFieldDetected(Message amqpMessage) {
        try {
            String rawJson = new String(amqpMessage.getBody(), StandardCharsets.UTF_8);
            FieldDetectedMessage message = objectMapper.readValue(rawJson, FieldDetectedMessage.class);

            if (message.matchId() == null || message.matchId().isBlank()) {
                log.warn("field.detected message ignored — missing matchId");
                return;
            }

            UUID matchId = UUID.fromString(message.matchId());

            if ("ERROR".equalsIgnoreCase(message.status())) {
                matchService.markFieldDetectionFailed(matchId, message.errorMessage());
                sseService.notifyClient(matchId.toString(), "ERROR");
                return;
            }

            String publicImageUrl = rewriteToPublicHost(message.defishedImageUrl());
            String cornersJson = objectMapper.writeValueAsString(message.corners());

            matchService.applyFieldDetectionResult(matchId, publicImageUrl, cornersJson);

            // Non-terminating event: the same emitter must keep streaming until ML+encoding
            // either complete or fail, so the user can be notified of those too.
            Map<String, Object> payload = new HashMap<>();
            payload.put("defishedImageUrl", publicImageUrl);
            payload.put("corners", message.corners() == null ? List.<Corner>of() : message.corners());
            sseService.sendEvent(matchId.toString(), "FIELD_DETECTED", payload);
        } catch (Exception ex) {
            log.error("Failed to process field.detected message", ex);
        }
    }

    private String rewriteToPublicHost(String url) {
        if (url == null || url.isBlank() || internalEndpoint == null || publicEndpoint == null) {
            return url;
        }
        if (url.startsWith(internalEndpoint)) {
            return publicEndpoint + url.substring(internalEndpoint.length());
        }
        return url;
    }
}
