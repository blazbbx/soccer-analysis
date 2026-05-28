package com.example.footballanalysis.controller;

import com.example.footballanalysis.service.MatchIngestionOrchestrator;
import com.example.footballanalysis.service.SseNotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/webhooks")
@Slf4j
public class MinioWebhookController {

    private final MatchIngestionOrchestrator matchIngestionOrchestrator;

    @PostMapping("/minio")
    public ResponseEntity<String> handleMinioWebhook(@RequestBody JsonNode payload) {
        try {
            // The Controller acts as a simple traffic cop, passing the data to the Brains
            matchIngestionOrchestrator.triggerProcessingPipeline(payload);

        } catch (Exception e) {
            log.error("Failed to process MinIO webhook payload", e);
            // We ALWAYS return 200 OK even on error, otherwise MinIO will panic and retry forever
        }

        return ResponseEntity.ok("Webhook received and acknowledged");
    }
}