package com.example.footballanalysis.model.requests;

import java.util.List;
import java.util.UUID;

public record AddHomeworkAssigneesRequest(
        List<UUID> playerProfileIds
) {}
