package com.example.footballanalysis.service;

import ch.qos.logback.classic.Level;
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

import static org.assertj.core.api.Assertions.assertThat;
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

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(matchRepository.save(match)).thenReturn(match);

        WorkerResultListener listener = new WorkerResultListener(OBJECT_MAPPER, sseService, matchRepository, clipService);

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

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(matchRepository.save(match)).thenReturn(match);

        WorkerResultListener listener = new WorkerResultListener(OBJECT_MAPPER, sseService, matchRepository, clipService);

        try (LogCaptureSession logs = LogCaptureSession.capture(WorkerResultListener.class, Level.INFO)) {
            listener.handleEncoderResult(amqpMessage(json));

            assertThat(logs.events())
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.WARN);
                        assertThat(event.getFormattedMessage()).isEqualTo("ENCODER worker failed for match " + matchId + ": encoder crashed");
                    });
        }

        verify(sseService).notifyClient(matchId.toString(), "ERROR");
        verify(matchRepository).save(match);
    }

    @Test
    void invalidPayload_logsCriticalFailure() {
        WorkerResultListener listener = new WorkerResultListener(OBJECT_MAPPER, sseService, matchRepository, clipService);

        try (LogCaptureSession logs = LogCaptureSession.capture(WorkerResultListener.class, Level.INFO)) {
            listener.handleMlResult(amqpMessage(jsonWithInvalidMatchId()));

            assertThat(logs.events())
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                        assertThat(event.getFormattedMessage()).isEqualTo("Critical failure while processing RabbitMQ message for worker ML");
                    });
        }
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