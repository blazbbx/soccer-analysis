package com.example.footballanalysis.model.responses;

import java.util.UUID;

public record ClipUploadResponse(
        UUID clipId,
        UUID matchId,
        String matchDisplayName,
        String uploadUrl,
        String storagePath
) {}