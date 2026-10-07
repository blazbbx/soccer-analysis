package com.example.footballanalysis.model.responses;

import java.time.LocalDateTime;
import java.util.UUID;

public record ParentInviteResponse(
        UUID id,
        String token,
        int maxUses,
        int usedCount,
        LocalDateTime expiresAt,
        boolean expired
) {
}
