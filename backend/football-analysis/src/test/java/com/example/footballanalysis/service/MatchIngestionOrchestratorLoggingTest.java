package com.example.footballanalysis.service;

import ch.qos.logback.classic.Level;
import com.example.footballanalysis.config.RabbitMQConfig;
import com.example.footballanalysis.dto.VideoProcessingStartMessage;
import com.example.footballanalysis.exception.ExternalServiceException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatchIngestionOrchestrator log tesztek")
class MatchIngestionOrchestratorLoggingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private MatchService matchService;

    @Test
    void testEvent_logsDebugAndSkipsPublishing() throws Exception {
        MatchIngestionOrchestrator orchestrator = new MatchIngestionOrchestrator(rabbitTemplate, matchService);
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

        verifyNoInteractions(rabbitTemplate, matchService);
    }

    @Test
    void webhookPayload_logsDebugAndPublishesToBothWorkerQueues() throws Exception {
        MatchIngestionOrchestrator orchestrator = new MatchIngestionOrchestrator(rabbitTemplate, matchService);
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

        doNothing().when(matchService).markMatchAsProcessing("video file.mp4");

        try (LogCaptureSession logs = LogCaptureSession.capture(MatchIngestionOrchestrator.class, Level.DEBUG)) {
            orchestrator.triggerProcessingPipeline(payload);

            assertThat(logs.events())
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
                        assertThat(event.getFormattedMessage()).isEqualTo("MinIO webhook triggered for file video file.mp4 in bucket raw-videos");
                    });

            assertThat(logs.events())
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
                        assertThat(event.getFormattedMessage()).isEqualTo("Published processing message for file video file.mp4 to worker queues");
                    });
        }

        verify(matchService).markMatchAsProcessing("video file.mp4");
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME), eq(RabbitMQConfig.ENCODER_ROUTING_KEY), any(VideoProcessingStartMessage.class));
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME), eq(RabbitMQConfig.ML_ROUTING_KEY), any(VideoProcessingStartMessage.class));
    }

    @Test
    void amqpFailure_throwsExternalServiceException() throws Exception {
        MatchIngestionOrchestrator orchestrator = new MatchIngestionOrchestrator(rabbitTemplate, matchService);
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

        doNothing().when(matchService).markMatchAsProcessing("video file.mp4");
        doThrow(new AmqpException("boom"))
                .when(rabbitTemplate)
                .convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME), eq(RabbitMQConfig.ENCODER_ROUTING_KEY), any(VideoProcessingStartMessage.class));

        try (LogCaptureSession logs = LogCaptureSession.capture(MatchIngestionOrchestrator.class, Level.DEBUG)) {
            assertThatThrownBy(() -> orchestrator.triggerProcessingPipeline(payload))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessage("Failed to send the processing message to worker services.");

            assertThat(logs.events())
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
                        assertThat(event.getFormattedMessage()).isEqualTo("MinIO webhook triggered for file video file.mp4 in bucket raw-videos");
                    });
        }
    }
}