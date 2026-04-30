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
        } catch (Exception ex) {
            log.error("Failed to process clip render completion message", ex);
        }
    }

    // The Master Aggregator Logic
    private void processWorkerResult(Message amqpMessage, String workerType) {
        try {
            String rawJsonMessage = new String(amqpMessage.getBody(), StandardCharsets.UTF_8);
            VideoProcessingCompletedMessage message = objectMapper.readValue(rawJsonMessage, VideoProcessingCompletedMessage.class);

            UUID matchId = UUID.fromString(message.matchId());
            Match match = matchRepository.findById(matchId)
                    .orElseThrow(() -> new NotFoundException("Match not found for ID: " + matchId));
            log.info("Received {} worker result for match {} with status {}", workerType, matchId, message.status());
            // --- 1. FAIL FAST: Did the Python worker report a crash? ---
            if ("ERROR".equalsIgnoreCase(message.status())) {
                log.warn("{} worker failed for match {}: {}", workerType, matchId, message.errorMessage());

                updateSpecificWorker(match, workerType, "FAILED", message);
                match.setOverallStatus("ERROR");
                matchRepository.save(match);

                // Tell React immediately! (You can even pass the error message if your frontend wants it)
                sseService.notifyClient(match.getId().toString(), "ERROR");
                return; // Stop processing, we are done here.
            }

            // --- 2. SUCCESS: Update the specific worker's data ---
            updateSpecificWorker(match, workerType, "COMPLETED", message);

            // --- 3. CHECK FINISH LINE: Are we 100% done? ---
            if (match.isFullyProcessed()) {
                match.setOverallStatus("COMPLETED");
                log.info("Both workers finished for match {}; match is fully ready", match.getId());

                sseService.notifyClient(match.getId().toString(), "COMPLETED");
            } else {
                log.info("{} worker finished for match {}; waiting on the other worker", workerType, match.getId());
            }

            matchRepository.save(match);

        } catch (Exception e) {
            log.error("Critical failure while processing RabbitMQ message for worker {}", workerType, e);
            // If the JSON parsing or DB completely explodes, you could attempt a fallback error notify here
        }
    }

    private void updateSpecificWorker(Match match, String workerType, String targetStatus, VideoProcessingCompletedMessage message) {
        switch (workerType) {
            case "ML" -> {
                match.setMlStatus(targetStatus);
                if ("COMPLETED".equals(targetStatus)) match.setTrackingDataUrl(message.trackingDataUrl());
            }
            case "ENCODER" -> {
                match.setEncodingStatus(targetStatus);
                if ("COMPLETED".equals(targetStatus)) match.setHlsManifestUrl(message.hlsUrl());
            }
            default -> throw new IllegalArgumentException("Unknown worker type: " + workerType);
        }
    }
}