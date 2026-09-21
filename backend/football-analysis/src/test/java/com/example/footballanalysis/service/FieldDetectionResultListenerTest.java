package com.example.footballanalysis.service;

import com.example.footballanalysis.dto.FieldDetectedMessage;
import com.example.footballanalysis.model.Corner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("FieldDetectionResultListener")
class FieldDetectionResultListenerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private SseNotificationService sseService;

    @Mock
    private MatchService matchService;

    private FieldDetectionResultListener listener;

    @BeforeEach
    void setUp() {
        listener = new FieldDetectionResultListener(OBJECT_MAPPER, sseService, matchService);
        ReflectionTestUtils.setField(listener, "internalEndpoint", "http://minio:9000");
        ReflectionTestUtils.setField(listener, "publicEndpoint", "http://localhost:9000");
    }

    @Test
    void invalidJson_isPoisonAndNotRequeued() {
        assertThatThrownBy(() -> listener.handleFieldDetected(amqp("{not-json")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(matchService, never()).applyFieldDetectionResult(any(), any(), any());
    }

    @Test
    void invalidMatchId_isPoisonAndNotRequeued() throws Exception {
        String json = OBJECT_MAPPER.writeValueAsString(new FieldDetectedMessage(
                "not-a-uuid", "COMPLETED", "http://minio:9000/field-detection/a.png", List.of(), null));

        assertThatThrownBy(() -> listener.handleFieldDetected(amqp(json)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(matchService, never()).applyFieldDetectionResult(any(), any(), any());
    }

    @Test
    void missingMatchId_isIgnored() throws Exception {
        String json = OBJECT_MAPPER.writeValueAsString(new FieldDetectedMessage(
                "  ", "COMPLETED", "http://minio:9000/field-detection/a.png", List.of(), null));

        listener.handleFieldDetected(amqp(json));

        verify(matchService, never()).applyFieldDetectionResult(any(), any(), any());
        verify(sseService, never()).sendEvent(anyString(), anyString(), any());
    }

    @Test
    void errorStatus_marksMatchFailedAndNotifies() throws Exception {
        UUID matchId = UUID.randomUUID();
        String json = OBJECT_MAPPER.writeValueAsString(new FieldDetectedMessage(
                matchId.toString(), "ERROR", null, null, "model crashed"));

        listener.handleFieldDetected(amqp(json));

        verify(matchService).markFieldDetectionFailed(matchId, "model crashed");
        verify(sseService).notifyClient(matchId.toString(), "ERROR");
        verify(matchService, never()).applyFieldDetectionResult(any(), any(), any());
    }

    @Test
    void success_rewritesInternalMinioHostAndKeepsSseOpen() throws Exception {
        UUID matchId = UUID.randomUUID();
        List<Corner> corners = List.of(new Corner(1, 2), new Corner(3, 4));
        String json = OBJECT_MAPPER.writeValueAsString(new FieldDetectedMessage(
                matchId.toString(),
                "COMPLETED",
                "http://minio:9000/field-detection/frame.png",
                corners,
                null));

        listener.handleFieldDetected(amqp(json));

        verify(matchService).applyFieldDetectionResult(
                eq(matchId),
                eq("http://localhost:9000/field-detection/frame.png"),
                eq(OBJECT_MAPPER.writeValueAsString(corners)));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(sseService).sendEvent(eq(matchId.toString()), eq("FIELD_DETECTED"), payloadCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(payload.get("defishedImageUrl")).isEqualTo("http://localhost:9000/field-detection/frame.png");
        assertThat(payload.get("corners")).isEqualTo(corners);
        verify(sseService, never()).notifyClient(anyString(), anyString());
    }

    @Test
    void transientMatchServiceFailure_isRequeued() throws Exception {
        UUID matchId = UUID.randomUUID();
        String json = OBJECT_MAPPER.writeValueAsString(new FieldDetectedMessage(
                matchId.toString(), "COMPLETED", "http://minio:9000/a.png", List.of(), null));
        doThrow(new RuntimeException("db down")).when(matchService)
                .applyFieldDetectionResult(any(), any(), any());

        assertThatThrownBy(() -> listener.handleFieldDetected(amqp(json)))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("db down");
    }

    private static Message amqp(String json) {
        return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
