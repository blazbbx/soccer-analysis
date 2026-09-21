package com.example.footballanalysis.service;

import ch.qos.logback.classic.Level;
import com.example.footballanalysis.dto.ClipRenderCompletedMessage;
import com.example.footballanalysis.dto.VideoProcessingCompletedMessage;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.testsupport.LogCaptureSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkerResultListener log tesztek")
class WorkerResultListenerLoggingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private SseNotificationService sseService;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private ClipService clipService;

    @Test
    void completedWorker_logsReceivedAndWaitingMessage() throws Exception {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId, "PENDING", "PENDING");
        String json = json(matchId, "COMPLETED", null);

        when(matchRepository.findByIdForUpdate(matchId)).thenReturn(Optional.of(match));
        when(matchRepository.save(match)).thenReturn(match);

        WorkerResultListener listener = listener();

        try (LogCaptureSession logs = LogCaptureSession.capture(WorkerResultListener.class, Level.INFO)) {
            listener.handleMlResult(amqpMessage(json));

            assertThat(logs.events())
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.INFO);
                        assertThat(event.getFormattedMessage()).isEqualTo("Received ML worker result for match " + matchId + " with status COMPLETED");
                    });

            assertThat(logs.events())
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.INFO);
                        assertThat(event.getFormattedMessage()).isEqualTo("ML worker finished for match " + matchId + "; waiting on the other worker");
                    });
        }

        verify(matchRepository).save(match);
    }

    @Test
    void errorWorker_logsWarningAndNotifiesFrontend() throws Exception {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId, "PENDING", "PENDING");
        String json = json(matchId, "ERROR", "encoder crashed");

        when(matchRepository.findByIdForUpdate(matchId)).thenReturn(Optional.of(match));
        when(matchRepository.save(match)).thenReturn(match);

        WorkerResultListener listener = listener();

        try (LogCaptureSession logs = LogCaptureSession.capture(WorkerResultListener.class, Level.INFO)) {
            listener.handleEncoderResult(amqpMessage(json));

            assertThat(logs.events())
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.WARN);
                        assertThat(event.getFormattedMessage()).isEqualTo("ENCODER worker failed for match " + matchId + ": encoder crashed");
                    });
        }

        verify(sseService).notifyClientAfterCommit(matchId.toString(), "ERROR");
        verify(matchRepository).save(match);
    }

    @Test
    void invalidPayload_logsCriticalFailure() {
        WorkerResultListener listener = listener();

        try (LogCaptureSession logs = LogCaptureSession.capture(WorkerResultListener.class, Level.INFO)) {
            assertThatThrownBy(() -> listener.handleMlResult(amqpMessage(jsonWithInvalidMatchId())))
                    .isInstanceOf(AmqpRejectAndDontRequeueException.class);

            assertThat(logs.events())
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                        assertThat(event.getFormattedMessage()).isEqualTo("Poison RabbitMQ message rejected for worker ML");
                    });
        }
    }

    @Test
    void completedWorker_rejectsUrlOutsideMinio() throws Exception {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId, "PENDING", "PENDING");
        when(matchRepository.findByIdForUpdate(matchId)).thenReturn(Optional.of(match));

        WorkerResultListener listener = new WorkerResultListener(
                OBJECT_MAPPER,
                sseService,
                matchRepository,
                clipService,
                new WorkerArtifactUrlValidator(java.util.List.of("http://localhost:9000")));

        assertThatThrownBy(() -> listener.handleMlResult(amqpMessage(json(matchId, "COMPLETED", null))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void completedWorker_acceptsMinioUrlAndStoresIt() throws Exception {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId, "PENDING", "PENDING");
        when(matchRepository.findByIdForUpdate(matchId)).thenReturn(Optional.of(match));
        when(matchRepository.save(match)).thenReturn(match);

        WorkerResultListener listener = new WorkerResultListener(
                OBJECT_MAPPER,
                sseService,
                matchRepository,
                clipService,
                new WorkerArtifactUrlValidator(java.util.List.of("http://localhost:9000")));

        String json = OBJECT_MAPPER.writeValueAsString(new VideoProcessingCompletedMessage(
                matchId.toString(),
                "http://localhost:9000/tracking-data/track.json",
                "http://localhost:9000/hls-streams/index.m3u8",
                "COMPLETED",
                null));

        listener.handleMlResult(amqpMessage(json));

        assertThat(match.getTrackingDataUrl()).isEqualTo("http://localhost:9000/tracking-data/track.json");
        verify(matchRepository).save(match);
    }

    @Test
    void clipRender_processingCompletedAndFailedDelegateToClipService() throws Exception {
        UUID clipId = UUID.randomUUID();
        WorkerResultListener listener = listener();

        listener.handleClipRenderResult(amqpMessage(OBJECT_MAPPER.writeValueAsString(
                new ClipRenderCompletedMessage(clipId.toString(), "PROCESSING", null))));
        verify(clipService).markRenderProcessing(clipId);

        listener.handleClipRenderResult(amqpMessage(OBJECT_MAPPER.writeValueAsString(
                new ClipRenderCompletedMessage(clipId.toString(), "READY", null))));
        verify(clipService).markRenderCompleted(clipId);

        listener.handleClipRenderResult(amqpMessage(OBJECT_MAPPER.writeValueAsString(
                new ClipRenderCompletedMessage(clipId.toString(), "ERROR", "encode failed"))));
        verify(clipService).markRenderFailed(clipId, "encode failed");
    }

    @Test
    void clipRender_invalidClipId_isPoison() {
        WorkerResultListener listener = listener();

        assertThatThrownBy(() -> listener.handleClipRenderResult(amqpMessage(
                "{\"clipId\":\"not-a-uuid\",\"status\":\"COMPLETED\"}")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    private WorkerResultListener listener() {
        return new WorkerResultListener(
                OBJECT_MAPPER,
                sseService,
                matchRepository,
                clipService,
                new WorkerArtifactUrlValidator(java.util.List.of()));
    }

    private static Message amqpMessage(String json) {
        return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    private static Match match(UUID id, String mlStatus, String encodingStatus) {
        Match match = new Match();
        match.setId(id);
        match.setMlStatus(mlStatus);
        match.setEncodingStatus(encodingStatus);
        return match;
    }

    private static String json(UUID matchId, String status, String errorMessage) throws Exception {
        return OBJECT_MAPPER.writeValueAsString(new VideoProcessingCompletedMessage(
                matchId.toString(),
                "https://tracking.example/data.json",
                "https://video.example/playlist.m3u8",
                status,
                errorMessage
        ));
    }

    private static String jsonWithInvalidMatchId() {
        return """
                {"matchId":"not-a-uuid","trackingDataUrl":null,"hlsUrl":null,"status":"COMPLETED","errorMessage":null}
                """;
    }
}