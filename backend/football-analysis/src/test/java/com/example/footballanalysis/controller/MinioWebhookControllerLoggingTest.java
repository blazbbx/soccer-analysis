package com.example.footballanalysis.controller;

import ch.qos.logback.classic.Level;
import com.example.footballanalysis.service.MatchIngestionOrchestrator;
import com.example.footballanalysis.testsupport.LogCaptureSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("MinioWebhookController log tesztek")
class MinioWebhookControllerLoggingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private MatchIngestionOrchestrator matchIngestionOrchestrator;

    @Test
    void orchestratorFailure_logsErrorAndStillReturnsOk() throws Exception {
        MinioWebhookController controller = new MinioWebhookController(matchIngestionOrchestrator);
        var payload = OBJECT_MAPPER.readTree("{}");

        doThrow(new IllegalStateException("boom"))
                .when(matchIngestionOrchestrator)
                .triggerProcessingPipeline(payload);

        try (LogCaptureSession logs = LogCaptureSession.capture(MinioWebhookController.class, Level.ERROR)) {
            var response = controller.handleMinioWebhook(payload);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isEqualTo("Webhook received and acknowledged");
            assertThat(logs.events())
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                        assertThat(event.getFormattedMessage()).isEqualTo("Failed to process MinIO webhook payload");
                    });
        }

        verify(matchIngestionOrchestrator).triggerProcessingPipeline(payload);
    }
}