package com.example.footballanalysis.service;

import com.example.footballanalysis.config.RabbitMQConfig;
import com.example.footballanalysis.dto.ClipRenderCompletedMessage;
import com.example.footballanalysis.dto.VideoProcessingCompletedMessage;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.repository.MatchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkerResultListener {

    private final ObjectMapper objectMapper;
    private final SseNotificationService sseService;
    private final MatchRepository matchRepository;
    private final ClipService clipService;
    private final WorkerArtifactUrlValidator artifactUrlValidator;

    @RabbitListener(queues = RabbitMQConfig.ML_COMPLETED_QUEUE_NAME)
    @Transactional
    public void handleMlResult(Message amqpMessage) {
        processWorkerResult(amqpMessage, "ML");
    }

    @RabbitListener(queues = RabbitMQConfig.ENCODER_COMPLETED_QUEUE_NAME)
    @Transactional
    public void handleEncoderResult(Message amqpMessage) {
        processWorkerResult(amqpMessage, "ENCODER");
    }

    @RabbitListener(queues = RabbitMQConfig.CLIP_RENDER_COMPLETED_QUEUE_NAME)
    @Transactional
    public void handleClipRenderResult(Message amqpMessage) {
        try {
            String rawJsonMessage = new String(amqpMessage.getBody(), StandardCharsets.UTF_8);
            ClipRenderCompletedMessage message = objectMapper.readValue(rawJsonMessage, ClipRenderCompletedMessage.class);

            if (message.clipId() == null || message.clipId().isBlank()) {
                log.warn("Clip render completion message ignored because clipId is missing");
                return;
            }

            UUID clipId = UUID.fromString(message.clipId());
            String status = message.status() != null ? message.status().toUpperCase() : "";

            if ("PROCESSING".equals(status)) {
                clipService.markRenderProcessing(clipId);
                return;
            }

            if ("COMPLETED".equals(status) || "READY".equals(status)) {
                clipService.markRenderCompleted(clipId);
                return;
            }

            clipService.markRenderFailed(clipId, message.errorMessage());
        } catch (JsonProcessingException | IllegalArgumentException | NotFoundException ex) {
            log.error("Poison clip render completion message rejected", ex);
            throw new AmqpRejectAndDontRequeueException("Poison clip render completion message", ex);
        } catch (RuntimeException ex) {
            log.error("Failed to process clip render completion message", ex);
            throw ex;
        }
    }

    // The Master Aggregator Logic
    private void processWorkerResult(Message amqpMessage, String workerType) {
        try {
            String rawJsonMessage = new String(amqpMessage.getBody(), StandardCharsets.UTF_8);
            VideoProcessingCompletedMessage message = objectMapper.readValue(rawJsonMessage, VideoProcessingCompletedMessage.class);

            UUID matchId = UUID.fromString(message.matchId());
            Match match = matchRepository.findByIdForUpdate(matchId)
                    .orElseThrow(() -> new NotFoundException("Match not found for ID: " + matchId));
            log.info("Received {} worker result for match {} with status {}", workerType, matchId, message.status());
            // --- 1. FAIL FAST: Did the Python worker report a crash? ---
            if ("ERROR".equalsIgnoreCase(message.status())) {
                if ("COMPLETED".equalsIgnoreCase(match.getOverallStatus())) {
                    log.info("Ignoring late {} worker failure for completed match {}", workerType, matchId);
                    return;
                }
                log.warn("{} worker failed for match {}: {}", workerType, matchId, message.errorMessage());

                updateSpecificWorker(match, workerType, "FAILED", message);
                match.setOverallStatus("ERROR");
                matchRepository.save(match);

                sseService.notifyClientAfterCommit(match.getId().toString(), "ERROR");
                return;
            }

            // --- 2. SUCCESS: Update the specific worker's data ---
            updateSpecificWorker(match, workerType, "COMPLETED", message);

            // --- 3. CHECK FINISH LINE: Are we 100% done? ---
            if (match.isFullyProcessed()) {
                match.setOverallStatus("COMPLETED");
                log.info("Both workers finished for match {}; match is fully ready", match.getId());

                sseService.notifyClientAfterCommit(match.getId().toString(), "COMPLETED");
            } else {
                log.info("{} worker finished for match {}; waiting on the other worker", workerType, match.getId());
            }

            matchRepository.save(match);

        } catch (JsonProcessingException | IllegalArgumentException | NotFoundException e) {
            log.error("Poison RabbitMQ message rejected for worker {}", workerType, e);
            throw new AmqpRejectAndDontRequeueException("Poison worker result for " + workerType, e);
        } catch (RuntimeException e) {
            log.error("Critical failure while processing RabbitMQ message for worker {}", workerType, e);
            throw e;
        }
    }

    private void updateSpecificWorker(Match match, String workerType, String targetStatus, VideoProcessingCompletedMessage message) {
        switch (workerType) {
            case "ML" -> {
                match.setMlStatus(targetStatus);
                if ("COMPLETED".equals(targetStatus)) {
                    match.setTrackingDataUrl(artifactUrlValidator.requireAllowed(message.trackingDataUrl()));
                }
            }
            case "ENCODER" -> {
                match.setEncodingStatus(targetStatus);
                if ("COMPLETED".equals(targetStatus)) {
                    match.setHlsManifestUrl(artifactUrlValidator.requireAllowed(message.hlsUrl()));
                }
            }
            default -> throw new IllegalArgumentException("Unknown worker type: " + workerType);
        }
    }
}