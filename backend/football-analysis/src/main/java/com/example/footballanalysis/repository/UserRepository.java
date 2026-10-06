package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    long countByRole(UserRole role);

    @Query("""
            select u from User u
            where u.id in (
                select link.user.id from PlayerParent link
                where link.playerProfile.id in (
                    select roster.playerProfile.id from TeamRoster roster where roster.team.id = :teamId
                )
              )
            order by u.lastName, u.firstName
            """)
    List<User> findParentsOnTeam(@Param("teamId") UUID teamId);

    @Query(value = """
            SELECT DISTINCT members.user_id FROM (
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
            """, nativeQuery = true)
    List<UUID> findUserIdsInClub(@Param("clubId") UUID clubId);

    @Query("""
            select u from User u
            where u.id in :ids
            order by u.lastName, u.firstName
            """)
    List<User> findByIdInOrdered(@Param("ids") Collection<UUID> ids);

    default List<User> findUsersInClub(UUID clubId) {
        List<UUID> ids = findUserIdsInClub(clubId);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return findByIdInOrdered(ids);
    }

    @Query("select u.id from User u where u.role = :role")
    List<UUID> findIdsByRole(@Param("role") UserRole role);
}
