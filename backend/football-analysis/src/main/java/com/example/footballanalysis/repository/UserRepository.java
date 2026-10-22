package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
            select user from User user
            order by user.lastName asc, user.firstName asc, user.id asc
            """)
    Page<User> findPage(Pageable pageable);

    @Query("""
            select user from User user
            where lower(coalesce(user.firstName, '')) like :pattern escape '\\'
               or lower(coalesce(user.lastName, '')) like :pattern escape '\\'
               or lower(user.email) like :pattern escape '\\'
            order by user.lastName asc, user.firstName asc, user.id asc
            """)
    Page<User> findPageMatching(@Param("pattern") String pattern, Pageable pageable);

    @Query(value = """
            SELECT found.user_id FROM (
            """ + ClubMembershipSql.MEMBER_IDS + """
            ) found
            WHERE found.user_id = :userId
            LIMIT 1
            """, nativeQuery = true)
    List<UUID> findUserIdInClub(@Param("clubId") UUID clubId, @Param("userId") UUID userId);

    @Query(value = """
            SELECT u.id FROM users u
            WHERE u.id IN (
            """ + ClubMembershipSql.MEMBER_IDS + """
            )
            AND
            """ + ClubMembershipSql.TEXT_MATCH + """
            ORDER BY u.last_name ASC NULLS LAST, u.first_name ASC NULLS LAST, u.id ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<UUID> findUserIdsInClubPage(
            @Param("clubId") UUID clubId,
            @Param("q") String query,
            @Param("pattern") String pattern,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Query(value = """
            SELECT count(*) FROM users u
            WHERE u.id IN (
            """ + ClubMembershipSql.MEMBER_IDS + """
            )
            AND
            """ + ClubMembershipSql.TEXT_MATCH,
            nativeQuery = true)
    long countUsersInClub(
            @Param("clubId") UUID clubId,
            @Param("q") String query,
            @Param("pattern") String pattern);

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

    @Query("""
            select roster.team.id, link.user
            from TeamRoster roster, PlayerParent link
            where link.playerProfile.id = roster.playerProfile.id
              and roster.team.id in :teamIds
            """)
    List<Object[]> findParentsByTeamIds(@Param("teamIds") Collection<UUID> teamIds);

    @Query("select u.id from User u where u.role = :role")
    List<UUID> findIdsByRole(@Param("role") UserRole role);
}
