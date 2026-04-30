package com.example.footballanalysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClipRenderCompletedMessage(
        String clipId,
        String status,
        String errorMessage
) {}
