package com.example.footballanalysis.model.requests;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record UpdateTrackingLabelDataRequest(
        @NotNull(message = "{validation.match.labelData.required}")
        JsonNode labelData
) {}
