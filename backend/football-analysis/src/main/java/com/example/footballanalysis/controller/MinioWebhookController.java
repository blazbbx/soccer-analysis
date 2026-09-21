package com.example.footballanalysis.controller;

import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.exception.WebhookPayloadException;
import com.example.footballanalysis.service.MatchIngestionOrchestrator;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/webhooks")
@Slf4j
public class MinioWebhookController {

    private final MatchIngestionOrchestrator matchIngestionOrchestrator;
    private final String expectedAuthorization;

    public MinioWebhookController(
            MatchIngestionOrchestrator matchIngestionOrchestrator,
            @Value("${minio.webhook.authorization:}") String expectedAuthorization) {
        this.matchIngestionOrchestrator = matchIngestionOrchestrator;
        this.expectedAuthorization = expectedAuthorization == null ? "" : expectedAuthorization.trim();
    }

    @PostMapping("/minio")
    public ResponseEntity<String> handleMinioWebhook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody JsonNode payload) {
        verifyWebhookAuthorization(authorization);
        try {
            log.info("Accepted MinIO webhook");
            matchIngestionOrchestrator.triggerProcessingPipeline(payload);
            return ResponseEntity.ok("Webhook received and acknowledged");
        } catch (WebhookPayloadException | NotFoundException e) {
            log.warn("MinIO webhook payload cannot be processed: {}", e.getMessage());
            return ResponseEntity.ok("Webhook ignored");
        } catch (Exception e) {
            log.error("Failed to process MinIO webhook payload", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Webhook processing failed");
        }
    }

    private void verifyWebhookAuthorization(String authorization) {
        if (expectedAuthorization.isBlank()) {
            log.warn("MinIO webhook authorization is not configured; rejecting request");
            throw new UnauthorizedException(
                    "error.auth.unauthorized",
                    new Object[0],
                    "Webhook authentication is not configured.");
        }
        if (authorization == null || !constantTimeEquals(expectedAuthorization, authorization.trim())) {
            throw new UnauthorizedException(
                    "error.auth.unauthorized",
                    new Object[0],
                    "Invalid webhook authorization.");
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        if (expectedBytes.length != actualBytes.length) {
            return false;
        }
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}