package com.example.footballanalysis.dto;

import com.example.footballanalysis.model.Corner;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FieldDetectedMessage(
        String matchId,
        String status,
        String defishedImageUrl,
        List<Corner> corners,
        String errorMessage
) {}
