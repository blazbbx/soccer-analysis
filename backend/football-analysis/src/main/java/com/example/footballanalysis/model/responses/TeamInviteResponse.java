package com.example.footballanalysis.model.responses;

import com.example.footballanalysis.model.db.InviteKind;
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
        boolean expired,
        String childFirstName,
        String childLastName,
        InviteKind inviteKind
) {
    public TeamInviteResponse(
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
        this(id, teamId, teamName, invitedRole, token, inviteLink, maxUses, usedCount, remainingUses, expiresAt, expired, null, null, null);
    }
}