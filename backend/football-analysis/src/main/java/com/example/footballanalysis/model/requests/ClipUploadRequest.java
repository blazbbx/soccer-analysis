package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ClipUploadRequest(
        @NotBlank(message = "{validation.clip.originalFilename.required}")
        @Size(max = 255, message = "{validation.clip.originalFilename.max}")
        String originalFilename,

        @Size(max = 255, message = "{validation.clip.name.max}")
        String name,

        @NotNull(message = "{validation.clip.startSeconds.required}")
        @Min(value = 0, message = "{validation.clip.startSeconds.min}")
        Integer startSeconds,

        @NotNull(message = "{validation.clip.endSeconds.required}")
        @Min(value = 1, message = "{validation.clip.endSeconds.min}")
        Integer endSeconds
) {}