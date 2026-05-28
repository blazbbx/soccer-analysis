package com.example.footballanalysis.service;

import ch.qos.logback.classic.Level;
import com.example.footballanalysis.config.RabbitMQConfig;
import com.example.footballanalysis.dto.FieldDetectionStartMessage;
import com.example.footballanalysis.exception.ExternalServiceException;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.testsupport.LogCaptureSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Webhook flow now triggers only the field-detection worker. The encoder and ML workers
 * are dispatched separately by the confirm-corners endpoint (covered by other tests).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MatchIngestionOrchestrator log tesztek")
class MatchIngestionOrchestratorLoggingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private MatchService matchService;

    @Mock
    private S3PresignerService presignerService;

    private MatchIngestionOrchestrator newOrchestrator() {
        MatchIngestionOrchestrator orchestrator = new MatchIngestionOrchestrator(rabbitTemplate, matchService, presignerService);
        ReflectionTestUtils.setField(orchestrator, "rawVideoBucket", "raw-videos");
        return orchestrator;
    }

    @Test
    void testEvent_logsDebugAndSkipsPublishing() throws Exception {
        MatchIngestionOrchestrator orchestrator = newOrchestrator();
        JsonNode payload = OBJECT_MAPPER.readTree("""
                {"EventName":"s3:TestEvent"}
                """);

        try (LogCaptureSession logs = LogCaptureSession.capture(MatchIngestionOrchestrator.class, Level.DEBUG)) {
            orchestrator.triggerProcessingPipeline(payload);

            assertThat(logs.events())
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
                        assertThat(event.getFormattedMessage()).isEqualTo("Received MinIO setup test event");
                    });
        }

        verifyNoInteractions(rabbitTemplate, matchService, presignerService);
    }

    @Test
    void webhookPayload_publishesOnlyFieldDetectionMessage() throws Exception {
        MatchIngestionOrchestrator orchestrator = newOrchestrator();
        JsonNode payload = OBJECT_MAPPER.readTree("""
                {
                  "Records": [
                    {
                      "s3": {
                        "bucket": { "name": "raw-videos" },
                        "object": { "key": "video%20file.mp4" }
                      }
                    }
                  ]
                }
                """);

        UUID matchId = UUID.randomUUID();
        Match match = new Match();
        match.setId(matchId);
        match.setSavedMinioFileName("video file.mp4");

        when(matchService.markMatchAsPreprocessing("video file.mp4")).thenReturn(match);
        when(presignerService.generateInternalRawVideoDownloadUrl(eq("video file.mp4"), any(Duration.class)))
                .thenReturn("http://minio:9000/raw-videos/video%20file.mp4?sig=fake");

        try (LogCaptureSession logs = LogCaptureSession.capture(MatchIngestionOrchestrator.class, Level.DEBUG)) {
            orchestrator.triggerProcessingPipeline(payload);

            assertThat(logs.events())
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
                        assertThat(event.getFormattedMessage()).isEqualTo("MinIO webhook triggered for file video file.mp4 in bucket raw-videos");
                    });
        }

        verify(matchService).markMatchAsPreprocessing("video file.mp4");
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.FIELD_DETECTION_ROUTING_KEY),
                any(FieldDetectionStartMessage.class));
        // The ML and encoder queues are NOT published from the webhook anymore.
        verify(rabbitTemplate, org.mockito.Mockito.never()).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.ENCODER_ROUTING_KEY),
                any(Object.class));
        verify(rabbitTemplate, org.mockito.Mockito.never()).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.ML_ROUTING_KEY),
                any(Object.class));
    }

    @Test
    void amqpFailure_throwsExternalServiceException() throws Exception {
        MatchIngestionOrchestrator orchestrator = newOrchestrator();
        JsonNode payload = OBJECT_MAPPER.readTree("""
                {
                  "Records": [
                    {
                      "s3": {
                        "bucket": { "name": "raw-videos" },
                        "object": { "key": "video%20file.mp4" }
                      }
                    }
                  ]
                }
                """);

        UUID matchId = UUID.randomUUID();
        Match match = new Match();
        match.setId(matchId);
        match.setSavedMinioFileName("video file.mp4");

        when(matchService.markMatchAsPreprocessing("video file.mp4")).thenReturn(match);
        when(presignerService.generateInternalRawVideoDownloadUrl(eq("video file.mp4"), any(Duration.class)))
                .thenReturn("http://minio:9000/raw-videos/video%20file.mp4?sig=fake");
        doThrow(new AmqpException("boom"))
                .when(rabbitTemplate)
                .convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME), eq(RabbitMQConfig.FIELD_DETECTION_ROUTING_KEY), any(FieldDetectionStartMessage.class));

        try (LogCaptureSession logs = LogCaptureSession.capture(MatchIngestionOrchestrator.class, Level.DEBUG)) {
            assertThatThrownBy(() -> orchestrator.triggerProcessingPipeline(payload))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessage("Failed to send the field-detection message.");

            assertThat(logs.events())
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
                        assertThat(event.getFormattedMessage()).isEqualTo("MinIO webhook triggered for file video file.mp4 in bucket raw-videos");
                    });
        }
    }
}
