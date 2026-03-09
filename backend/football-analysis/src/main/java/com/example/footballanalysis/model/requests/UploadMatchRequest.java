package com.example.footballanalysis.model.requests;

import java.time.LocalDateTime;
import java.util.UUID;

public record UploadMatchRequest(
        String originalFilename,   // kötelező

        UUID homeTeamId,           // opcionális
        UUID awayTeamId,           // opcionális

        LocalDateTime matchDate    // opcionális: mikor játszották
) {}
