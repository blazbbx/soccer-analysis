package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ClipUpdateRequest(
        @Size(max = 255, message = "{validation.clip.name.max}")
        String name,

        @Min(value = 0, message = "{validation.clip.startSeconds.min}")
        Integer startSeconds,

        @Min(value = 1, message = "{validation.clip.endSeconds.min}")
        Integer endSeconds
) {}