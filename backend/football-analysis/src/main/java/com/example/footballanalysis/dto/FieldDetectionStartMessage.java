package com.example.footballanalysis.dto;

public record FieldDetectionStartMessage(
        String matchId,
        String videoUrl
) {}
