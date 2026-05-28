package com.example.footballanalysis.model.responses;

import java.util.UUID;

public record ClipCompositionUploadResponse(
        UUID clipId,
        String overlayUploadUrl,
        String audioUploadUrl,
        String timelineUploadUrl
) {}
