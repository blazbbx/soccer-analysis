package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.Size;

public record ClipCompositionUploadRequest(
        @Size(max = 255, message = "{validation.clip.overlayFilename.max}")
        String overlayFilename,

        @Size(max = 255, message = "{validation.clip.audioFilename.max}")
        String audioFilename,

        @Size(max = 255, message = "{validation.clip.timelineFilename.max}")
        String timelineFilename
) {}
