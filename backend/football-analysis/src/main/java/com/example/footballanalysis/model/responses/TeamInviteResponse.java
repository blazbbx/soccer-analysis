package com.example.footballanalysis.model.responses;

import com.example.footballanalysis.model.db.user.UserRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record TeamInviteResponse(
        UUID id,
        UUID teamId,
        String teamName,
        UserRole invitedRole,
        String token,
        String inviteLink,
        int maxUses,
        int usedCount,
        int remainingUses,
        LocalDateTime expiresAt,
        boolean expired
) {
}