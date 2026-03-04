package com.example.footballanalysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VideoProcessingCompletedMessage(
        String matchId,
        String trackingDataUrl,
        String hlsUrl,
        String status,
        String errorMessage
) {}
