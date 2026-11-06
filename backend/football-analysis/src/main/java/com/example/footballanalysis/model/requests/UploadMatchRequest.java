package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.NotBlank;
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

        // The jersey colors are optional at upload: the field-detection step suggests them and the
        // user confirms them together with the corners (ConfirmCornersRequest).
        @Size(max = 100, message = "{validation.match.color.max}")
        String homeTeamColor,      // opcionális

        @Size(max = 100, message = "{validation.match.color.max}")
        String awayTeamColor,      // opcionális

        @Size(max = 100, message = "{validation.match.color.max}")
        String refereeColor        // opcionális
) {}
