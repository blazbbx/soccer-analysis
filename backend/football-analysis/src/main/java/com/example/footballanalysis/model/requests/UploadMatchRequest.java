package com.example.footballanalysis.model.requests;

import java.util.UUID;

public record UploadMatchRequest(
        String originalFilename, // Used for DB display only (e.g., "game.mp4")
        UUID homeTeamId,         // Mandatory
        UUID awayTeamId,         // Optional
        String location          // Optional
) {}
