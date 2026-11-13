package com.example.footballanalysis.repository;

final class ClubMembershipSql {

    static final String MEMBER_IDS = """
            SELECT members.user_id FROM (
                SELECT membership.user_id AS user_id
                FROM club_admins membership
                WHERE membership.club_id = :clubId
                UNION
                SELECT ct.coach_id
                FROM coach_teams ct
                INNER JOIN teams t ON t.id = ct.team_id
                WHERE t.club_id = :clubId
                UNION
                SELECT ft.fan_id
                FROM fan_favorite_teams ft
                INNER JOIN teams t ON t.id = ft.team_id
                WHERE t.club_id = :clubId
                UNION
                SELECT profile.linked_user_id
                FROM team_roster roster
                INNER JOIN player_profiles profile ON profile.id = roster.player_profile_id
                INNER JOIN teams t ON t.id = roster.team_id
                WHERE t.club_id = :clubId
                  AND profile.linked_user_id IS NOT NULL
                UNION
                SELECT link.user_id
                FROM player_parents link
                WHERE link.player_profile_id IN (
                    SELECT roster.player_profile_id
                    FROM team_roster roster
                    INNER JOIN teams t ON t.id = roster.team_id
                    WHERE t.club_id = :clubId
                )
            ) members
            """;

    private ClubMembershipSql() {
    }
}
