package com.example.footballanalysis.model.requests;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PublishHomeworkRequest(
        boolean entireRoster,
        List<UUID> playerProfileIds,
        LocalDateTime expectedUpdatedAt
) {}
