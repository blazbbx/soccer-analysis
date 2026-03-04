package com.example.footballanalysis.dto;

// A standard Java Record. It will automatically serialize into clean JSON.
public record VideoProcessingStartMessage(
        String bucketName,
        String fileName,
        String action
) {}
