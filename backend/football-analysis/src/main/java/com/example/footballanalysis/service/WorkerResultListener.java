package com.example.footballanalysis.service;

import com.example.footballanalysis.config.RabbitMQConfig;
import com.example.footballanalysis.dto.VideoProcessingCompletedMessage;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.repository.MatchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WorkerResultListener {

    private final ObjectMapper objectMapper;
    private final SseNotificationService sseService;
    private final MatchRepository matchRepository;

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

    // The Master Aggregator Logic
    private void processWorkerResult(Message amqpMessage, String workerType) {
        try {
            String rawJsonMessage = new String(amqpMessage.getBody(), StandardCharsets.UTF_8);
            VideoProcessingCompletedMessage message = objectMapper.readValue(rawJsonMessage, VideoProcessingCompletedMessage.class);

            UUID matchId = UUID.fromString(message.matchId());
            Match match = matchRepository.findById(matchId)
                    .orElseThrow(() -> new RuntimeException("Match not found for ID: " + matchId));
            // --- 1. FAIL FAST: Did the Python worker report a crash? ---
            if ("ERROR".equalsIgnoreCase(message.status())) {
                System.err.println("❌ " + workerType + " WORKER FAILED: " + message.errorMessage());

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
                System.out.println("✅ BOTH WORKERS FINISHED! Match " + match.getId() + " is fully READY.");

                sseService.notifyClient(match.getId().toString(), "COMPLETED");
            } else {
                System.out.println("⏳ " + workerType + " finished, waiting on the other worker...");
            }

            matchRepository.save(match);

        } catch (Exception e) {
            System.err.println("CRITICAL: Failed to process RabbitMQ message: " + e.getMessage());
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