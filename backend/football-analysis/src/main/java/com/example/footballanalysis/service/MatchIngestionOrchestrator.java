package com.example.footballanalysis.service;

import com.example.footballanalysis.config.RabbitMQConfig;
import com.example.footballanalysis.dto.VideoProcessingStartMessage;
import com.example.footballanalysis.exception.ExternalServiceException;
import com.example.footballanalysis.exception.WebhookPayloadException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;


@Service
@RequiredArgsConstructor
public class MatchIngestionOrchestrator {

    private final RabbitTemplate rabbitTemplate;
    private final MatchService videoService;


    public void triggerProcessingPipeline(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            throw new WebhookPayloadException("Missing webhook payload.");
        }

        if (payload.has("EventName") && payload.get("EventName").asText().equals("s3:TestEvent")) {
            System.out.println("Received MinIO Setup Test Event");
            return;
        }

        JsonNode records = payload.get("Records");
        if (records == null || !records.isArray() || records.isEmpty()) {
            throw new WebhookPayloadException("Webhook payload does not contain any processable Records entries.");
        }

        JsonNode s3Object = records.get(0).path("s3");
        String bucketName = s3Object.path("bucket").path("name").asText(null);
        String rawKey = s3Object.path("object").path("key").asText(null);

        if (bucketName == null || bucketName.isBlank() || rawKey == null || rawKey.isBlank()) {
            throw new WebhookPayloadException("Webhook payload is missing the bucket name or object key.");
        }

        String fileName = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
        System.out.println("Webhook triggered! File ingested: " + fileName);

        VideoProcessingStartMessage message = new VideoProcessingStartMessage(bucketName, fileName, "START-PROCESSING");

        videoService.markMatchAsProcessing(fileName);

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    RabbitMQConfig.ENCODER_ROUTING_KEY,
                    message
            );

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    RabbitMQConfig.ML_ROUTING_KEY,
                    message
            );
        } catch (AmqpException ex) {
            throw new ExternalServiceException("Failed to send the processing message to worker services.", ex);
        }
    }
}