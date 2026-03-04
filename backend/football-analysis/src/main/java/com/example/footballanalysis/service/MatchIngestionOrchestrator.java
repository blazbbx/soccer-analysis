package com.example.footballanalysis.service;

import com.example.footballanalysis.config.RabbitMQConfig;
import com.example.footballanalysis.dto.VideoProcessingStartMessage;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
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
        // 1. Handle MinIO Setup Test Event
        if (payload.has("EventName") && payload.get("EventName").asText().equals("s3:TestEvent")) {
            System.out.println("Received MinIO Setup Test Event");
            return; // Exit early, nothing to process
        }

        // 2. Dig into the massive S3 JSON payload to find the bucket and filename
        JsonNode s3Object = payload.get("Records").get(0).get("s3");
        String bucketName = s3Object.get("bucket").get("name").asText();
        String rawKey = s3Object.get("object").get("key").asText();

        // 3. Extract and decode the filename
        String fileName = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
        System.out.println("Webhook triggered! File ingested: " + fileName);

        // 4. Create our clean, simple custom message
        VideoProcessingStartMessage message = new VideoProcessingStartMessage(
                bucketName,
                fileName,
                "START-PROCESSING"
        );

        videoService.markMatchAsProcessing(fileName);

        // 5. Publish to RabbitMQ to wake up both Python workers
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
    }
}