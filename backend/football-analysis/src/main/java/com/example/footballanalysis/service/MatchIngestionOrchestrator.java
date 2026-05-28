package com.example.footballanalysis.service;

import com.example.footballanalysis.config.RabbitMQConfig;
import com.example.footballanalysis.dto.FieldDetectionStartMessage;
import com.example.footballanalysis.dto.VideoProcessingStartMessage;
import com.example.footballanalysis.exception.ExternalServiceException;
import com.example.footballanalysis.exception.WebhookPayloadException;
import com.example.footballanalysis.model.Corner;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.user.User;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class MatchIngestionOrchestrator {

    // Field-detection worker pulls a frame ~3 minutes in; the presigned URL must outlive the
    // worker's ffprobe + ffmpeg passes. Plenty of margin for slow links.
    private static final Duration FIELD_DETECTION_URL_TTL = Duration.ofMinutes(30);

    private final RabbitTemplate rabbitTemplate;
    private final MatchService videoService;
    private final S3PresignerService presignerService;

    @Value("${minio.buckets.raw-videos}")
    private String rawVideoBucket;


    /**
     * Triggered by the MinIO upload webhook. Marks the match as PREPROCESSING and dispatches
     * the video to the field-detection worker only. The ML and encoder pipelines stay idle
     * until the user confirms the detected corners via {@link #triggerMlAndEncoding}.
     */
    public void triggerProcessingPipeline(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            throw new WebhookPayloadException("Missing webhook payload.");
        }

        if (payload.has("EventName") && payload.get("EventName").asText().equals("s3:TestEvent")) {
            log.debug("Received MinIO setup test event");
            return;
        }

        JsonNode records = payload.get("Records");
        if (records == null || !records.isArray() || records.isEmpty()) {
            throw new WebhookPayloadException("Webhook payload does not contain any processable Records entries.");
        }

        JsonNode s3Object = records.get(0).path("s3");
        String bucketName = s3Object.path("bucket").path("name").asText(null);
        String rawKey = s3Object.path("object").path("key").asText(null);

        if (bucketName == null || bucketName.isBlank() || rawKey == null || rawKey.isBlank()) {
            throw new WebhookPayloadException("Webhook payload is missing the bucket name or object key.");
        }

        String fileName = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
        log.debug("MinIO webhook triggered for file {} in bucket {}", fileName, bucketName);

        Match match = videoService.markMatchAsPreprocessing(fileName);
        String videoUrl = presignerService.generateInternalRawVideoDownloadUrl(fileName, FIELD_DETECTION_URL_TTL);

        FieldDetectionStartMessage message = new FieldDetectionStartMessage(
                match.getId().toString(),
                videoUrl
        );

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    RabbitMQConfig.FIELD_DETECTION_ROUTING_KEY,
                    message
            );
            log.debug("Published field-detection request for match {} (file {})", match.getId(), fileName);
        } catch (AmqpException ex) {
            throw new ExternalServiceException("Failed to send the field-detection message.", ex);
        }
    }

    /**
     * Entry point for the confirm-corners endpoint. Persists the corners (via MatchService)
     * and only then fans the work out to ML + encoder. Keeps the publishing logic and the
     * DB transition in the same call so the controller stays thin.
     */
    public Match confirmCornersAndDispatch(UUID matchId, List<Corner> corners, User actor) {
        Match match = videoService.confirmCornersAndStartProcessing(matchId, corners, actor);
        triggerMlAndEncoding(match, corners);
        return match;
    }

    /**
     * Called after the user confirms the four field corners. Fans the message out to both
     * the encoder and ML workers, carrying the user-corrected corners in the payload.
     */
    public void triggerMlAndEncoding(Match match, List<Corner> corners) {
        if (match == null || match.getId() == null) {
            throw new IllegalArgumentException("Match is required to trigger ML and encoding.");
        }

        String fileName = match.getSavedMinioFileName();
        VideoProcessingStartMessage message = new VideoProcessingStartMessage(
                rawVideoBucket,
                fileName,
                "START-PROCESSING",
                match.getId().toString(),
                corners,
                match.getHomeTeamColor(),
                match.getAwayTeamColor(),
                match.getRefereeColor()
        );

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    RabbitMQConfig.ENCODER_ROUTING_KEY,
                    message
            );
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    RabbitMQConfig.ML_ROUTING_KEY,
                    message
            );
            log.debug("Published encode + ML messages for match {} (file {})", match.getId(), fileName);
        } catch (AmqpException ex) {
            throw new ExternalServiceException("Failed to send the processing message to worker services.", ex);
        }
    }
}
