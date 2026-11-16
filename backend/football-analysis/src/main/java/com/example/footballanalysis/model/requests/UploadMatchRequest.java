package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record UploadMatchRequest(
        @NotBlank(message = "{validation.match.originalFilename.required}")
        @Size(max = 255, message = "{validation.match.originalFilename.max}")
        String originalFilename,   // kötelező

        UUID homeTeamId,           // opcionális
        UUID awayTeamId,           // opcionális

        @Size(max = 255, message = "{validation.match.originalFilename.max}")
        String awayTeamName,       // opcionális

        LocalDateTime matchDate,   // opcionális: mikor játszották

        @NotBlank(message = "{validation.match.color.required}")
        @Size(max = 100, message = "{validation.match.color.max}")
        String homeTeamColor,

        @NotBlank(message = "{validation.match.color.required}")
        @Size(max = 100, message = "{validation.match.color.max}")
        String awayTeamColor,

        @NotBlank(message = "{validation.match.color.required}")
        @Size(max = 100, message = "{validation.match.color.max}")
        String refereeColor,

        // Size of the video in bytes. Signed into the upload URL, so the stored file cannot be larger.
        @NotNull(message = "{validation.upload.size.required}")
        @Positive(message = "{validation.upload.size.required}")
        Long fileSize
) {}
