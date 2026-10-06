package com.example.footballanalysis.model.responses;

import com.example.footballanalysis.model.db.user.UserRole;

import java.util.List;
import java.util.UUID;

public record MeViewsResponse(
        UserRole primaryRole,
        List<UserRole> hats,
        List<UUID> clubAdminClubIds,
        List<UUID> coachTeamIds,
        List<UUID> parentProfileIds,
        List<UUID> playerTeamIds,
        List<UUID> fanTeamIds
) {}
