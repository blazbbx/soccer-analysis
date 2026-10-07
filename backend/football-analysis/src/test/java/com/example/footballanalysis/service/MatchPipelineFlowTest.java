package com.example.footballanalysis.service;

import com.example.footballanalysis.config.RabbitMQConfig;
import com.example.footballanalysis.dto.FieldDetectedMessage;
import com.example.footballanalysis.dto.FieldDetectionStartMessage;
import com.example.footballanalysis.dto.VideoProcessingCompletedMessage;
import com.example.footballanalysis.dto.VideoProcessingStartMessage;
import com.example.footballanalysis.model.Corner;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.repository.MatchRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Java-side pipeline: MinIO webhook → field-detect queue → worker result / SSE
 * → confirm corners → encoder+ML queues → both workers done → SSE COMPLETED.
 * Brokers and Python workers are mocked; this asserts the Spring wiring and order.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Match ingestion pipeline (Java chain)")
class MatchPipelineFlowTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private MatchService matchService;
    @Mock private S3PresignerService presignerService;
    @Mock private SseNotificationService sseService;
    @Mock private MatchRepository matchRepository;
    @Mock private ClipService clipService;

    private MatchIngestionOrchestrator orchestrator;
    private FieldDetectionResultListener fieldListener;
    private WorkerResultListener workerListener;

    @BeforeEach
    void setUp() {
        orchestrator = new MatchIngestionOrchestrator(rabbitTemplate, matchService, presignerService);
        ReflectionTestUtils.setField(orchestrator, "rawVideoBucket", "raw-videos");

        fieldListener = new FieldDetectionResultListener(OBJECT_MAPPER, sseService, matchService);
        ReflectionTestUtils.setField(fieldListener, "internalEndpoint", "http://minio:9000");
        ReflectionTestUtils.setField(fieldListener, "publicEndpoint", "http://localhost:9000");

        workerListener = new WorkerResultListener(
                OBJECT_MAPPER,
                sseService,
                matchRepository,
                clipService,
                new WorkerArtifactUrlValidator(List.of("http://localhost:9000")));
    }

    @Test
    void webhookThroughWorkers_notifiesFieldDetectedThenCompleted() throws Exception {
        UUID matchId = UUID.randomUUID();
        Match match = new Match();
        match.setId(matchId);
        match.setSavedMinioFileName("game.mp4");
        match.setMlStatus("PENDING");
        match.setEncodingStatus("PENDING");

        Coach actor = new Coach();
        actor.setId(UUID.randomUUID());
        actor.setRole(UserRole.COACH);
        List<Corner> corners = List.of(new Corner(10, 10), new Corner(20, 10), new Corner(20, 20), new Corner(10, 20));

        when(matchService.markMatchAsPreprocessing("game.mp4"))
                .thenReturn(new MatchService.PreprocessResult(match, true));
        when(presignerService.generateInternalRawVideoDownloadUrl(eq("game.mp4"), eq(Duration.ofMinutes(30))))
                .thenReturn("http://minio:9000/raw-videos/game.mp4");

        JsonNode webhook = OBJECT_MAPPER.readTree("""
                {
                  "Records": [
                    {
                      "s3": {
                        "bucket": { "name": "raw-videos" },
                        "object": { "key": "game.mp4" }
                      }
                    }
                  ]
                }
                """);
        orchestrator.triggerProcessingPipeline(webhook);

        verify(rabbitTemplate).convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.FIELD_DETECTION_ROUTING_KEY,
                new FieldDetectionStartMessage(matchId.toString(), "http://minio:9000/raw-videos/game.mp4"));

        String detectedJson = OBJECT_MAPPER.writeValueAsString(new FieldDetectedMessage(
                matchId.toString(),
                "COMPLETED",
                "http://minio:9000/field-detection/frame.png",
                corners,
                null));
        when(matchService.applyFieldDetectionResult(any(), any(), any())).thenReturn(true);
        fieldListener.handleFieldDetected(amqp(detectedJson));

        verify(matchService).applyFieldDetectionResult(
                eq(matchId),
                eq("http://localhost:9000/field-detection/frame.png"),
                eq(OBJECT_MAPPER.writeValueAsString(corners)));
        verify(sseService).sendEvent(eq(matchId.toString()), eq("FIELD_DETECTED"), any());

        when(matchService.confirmCornersAndStartProcessing(matchId, corners, actor))
                .thenReturn(new MatchService.CornerConfirmResult(match, true));
        orchestrator.confirmCornersAndDispatch(matchId, corners, actor);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.ENCODER_ROUTING_KEY),
                any(VideoProcessingStartMessage.class));
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.ML_ROUTING_KEY),
                any(VideoProcessingStartMessage.class));

        when(matchRepository.findByIdForUpdate(matchId)).thenReturn(Optional.of(match));
        when(matchRepository.save(match)).thenReturn(match);

        workerListener.handleEncoderResult(amqp(OBJECT_MAPPER.writeValueAsString(new VideoProcessingCompletedMessage(
                matchId.toString(),
                null,
                "http://localhost:9000/hls-streams/index.m3u8",
                "COMPLETED",
                null))));

        assertThat(match.getEncodingStatus()).isEqualTo("COMPLETED");
        assertThat(match.getHlsManifestUrl()).isEqualTo("http://localhost:9000/hls-streams/index.m3u8");
        verify(sseService, never()).notifyClientAfterCommit(matchId.toString(), "COMPLETED");

        workerListener.handleMlResult(amqp(OBJECT_MAPPER.writeValueAsString(new VideoProcessingCompletedMessage(
                matchId.toString(),
                "http://localhost:9000/tracking-data/track.json",
                null,
                "COMPLETED",
                null))));

        assertThat(match.getMlStatus()).isEqualTo("COMPLETED");
        assertThat(match.getTrackingDataUrl()).isEqualTo("http://localhost:9000/tracking-data/track.json");
        assertThat(match.getOverallStatus()).isEqualTo("COMPLETED");
        verify(sseService).notifyClientAfterCommit(matchId.toString(), "COMPLETED");
    }

    private static Message amqp(String json) {
        return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
