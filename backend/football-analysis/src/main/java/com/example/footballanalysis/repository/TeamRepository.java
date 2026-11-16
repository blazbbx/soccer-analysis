package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.Team;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

@Repository
public interface TeamRepository extends JpaRepository<Team, UUID> {
    @Override
    @EntityGraph(attributePaths = {"club"})
    java.util.Optional<Team> findById(UUID id);

    /**
     * Of the given users, those who may still read the team chat: coach, player, fan, parent of a
     * rostered player, or admin of the team's club. Same rule as UserAccessService.canAccessTeamChat
     * (without the platform admin), but from the database only, so it works for other users too.
     */
    @Query(value = """
            SELECT coach_id FROM coach_teams
             WHERE team_id = :teamId AND coach_id IN (:userIds)
            UNION
            SELECT p.linked_user_id FROM team_roster r
              JOIN player_profiles p ON p.id = r.player_profile_id
             WHERE r.team_id = :teamId AND p.linked_user_id IN (:userIds)
            UNION
            SELECT fan_id FROM fan_favorite_teams
             WHERE team_id = :teamId AND fan_id IN (:userIds)
            UNION
            SELECT pp.user_id FROM player_parents pp
              JOIN team_roster r ON r.player_profile_id = pp.player_profile_id
             WHERE r.team_id = :teamId AND pp.user_id IN (:userIds)
            UNION
            SELECT ca.user_id FROM club_admins ca
              JOIN teams t ON t.club_id = ca.club_id
             WHERE t.id = :teamId AND ca.user_id IN (:userIds)
            """, nativeQuery = true)
    java.util.List<UUID> findUserIdsWithChatAccess(
            @Param("teamId") UUID teamId, @Param("userIds") Collection<UUID> userIds);

    /** Of the given users, the admins of the team's club (chat role label). */
    @Query(value = """
            SELECT ca.user_id FROM club_admins ca
              JOIN teams t ON t.club_id = ca.club_id
             WHERE t.id = :teamId AND ca.user_id IN (:userIds)
            """, nativeQuery = true)
    java.util.List<UUID> findClubAdminIdsOfTeam(
            @Param("teamId") UUID teamId, @Param("userIds") Collection<UUID> userIds);

    /** Of the given users, the coaches of this team (chat role label). */
    @Query(value = """
            SELECT coach_id FROM coach_teams
             WHERE team_id = :teamId AND coach_id IN (:userIds)
            """, nativeQuery = true)
    java.util.List<UUID> findCoachIdsOfTeam(
            @Param("teamId") UUID teamId, @Param("userIds") Collection<UUID> userIds);

    /** Serializes publishing homework and removing a player, which both read the roster. No fetch join: PostgreSQL cannot lock an outer join. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Team t where t.id = :id")
    java.util.Optional<Team> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"club"})
    @Query("select t from Team t where t.id = :id")
    java.util.Optional<Team> findByIdWithClub(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"club"})
    java.util.List<Team> findByClub_Id(UUID clubId);

    @Query("select t.id from Team t where t.club.id = :clubId")
    java.util.List<UUID> findIdsByClub_Id(@Param("clubId") UUID clubId);

    @Query("select t.id from Team t where t.club.id in :clubIds")
    java.util.List<UUID> findIdsByClub_IdIn(@Param("clubIds") Collection<UUID> clubIds);

    @Query("select t.id from Team t")
    java.util.List<UUID> findAllTeamIds();

    @Query("""
            select t from Team t
            left join fetch t.club club
            where (:pattern is null
                or lower(t.name) like :pattern escape '\\'
                or lower(coalesce(t.shortName, '')) like :pattern escape '\\'
                or lower(coalesce(club.name, '')) like :pattern escape '\\')
            order by lower(t.name) asc, t.id asc
            """)
    java.util.List<Team> findCatalog(@Param("pattern") String pattern, Pageable pageable);

    @Query("""
            select count(t) from Team t
            left join t.club club
            where (:pattern is null
                or lower(t.name) like :pattern escape '\\'
                or lower(coalesce(t.shortName, '')) like :pattern escape '\\'
                or lower(coalesce(club.name, '')) like :pattern escape '\\')
            """)
    long countCatalog(@Param("pattern") String pattern);

    @Query("""
            select t from Team t
            left join fetch t.club club
            where t.id in :ids
              and (:pattern is null
                or lower(t.name) like :pattern escape '\\'
                or lower(coalesce(t.shortName, '')) like :pattern escape '\\'
                or lower(coalesce(club.name, '')) like :pattern escape '\\')
            order by lower(t.name) asc, t.id asc
            """)
    java.util.List<Team> findCatalogByIds(
            @Param("ids") Collection<UUID> ids,
            @Param("pattern") String pattern,
            Pageable pageable);

    @Query("""
            select count(t) from Team t
            left join t.club club
            where t.id in :ids
              and (:pattern is null
                or lower(t.name) like :pattern escape '\\'
                or lower(coalesce(t.shortName, '')) like :pattern escape '\\'
                or lower(coalesce(club.name, '')) like :pattern escape '\\')
            """)
    long countCatalogByIds(@Param("ids") Collection<UUID> ids, @Param("pattern") String pattern);

    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, UUID id);
}
