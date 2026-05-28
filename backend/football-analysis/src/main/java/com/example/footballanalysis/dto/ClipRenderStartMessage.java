package com.example.footballanalysis.dto;

public record ClipRenderStartMessage(
        String matchId,
        String clipId,
        String baseBucket,
        String baseObjectKey,
        String outputBucket,
        String action
) {}
