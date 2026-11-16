package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.user.User;
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

    @Query("""
            select user from User user
            order by user.lastName asc, user.firstName asc, user.id asc
            """)
    Page<User> findPage(Pageable pageable);

    /**
     * Teljes névre (mindkét sorrendben) vagy e-mailre keres. Natív SQL, hogy a feltétel betűre egyezzen
     * a V15 trigram indexeinek kifejezésével (lásd {@link UserSearchSql}).
     */
    @Query(value = """
            SELECT u.* FROM users u
            WHERE
            """ + UserSearchSql.NAME_OR_EMAIL_MATCH + """
            ORDER BY u.last_name ASC NULLS LAST, u.first_name ASC NULLS LAST, u.id ASC
            """,
            countQuery = "SELECT count(*) FROM users u WHERE " + UserSearchSql.NAME_OR_EMAIL_MATCH,
            nativeQuery = true)
    Page<User> findPageMatching(@Param("pattern") String pattern, Pageable pageable);

    @Query(value = """
            SELECT found.user_id FROM (
            """ + ClubMembershipSql.MEMBER_IDS + """
            ) found
            WHERE found.user_id = :userId
            LIMIT 1
            """, nativeQuery = true)
    List<UUID> findUserIdInClub(@Param("clubId") UUID clubId, @Param("userId") UUID userId);

    /** A klubtagok egy lapjának egy sora: a felhasználó id-ja és a lapozás előtti teljes találatszám. */
    interface ClubMemberRow {
        UUID getId();

        Long getTotal();
    }

    /**
     * A klubtagok egy lapja névre vagy e-mailre szűrve. A {@code count(*) OVER ()} a LIMIT előtt számol,
     * így a tagok összegyűjtése (MEMBER_IDS) egyszer fut, nem külön a lapra és a darabszámra.
     * Üres lapnál nincs sor, ezért darabszám sem: ilyenkor a {@link #countUsersInClub} kell.
     */
    @Query(value = """
            SELECT u.id AS id, count(*) OVER () AS total FROM users u
            WHERE u.id IN (
            """ + ClubMembershipSql.MEMBER_IDS + """
            )
            AND
            """ + UserSearchSql.NAME_OR_EMAIL_MATCH + """
            ORDER BY u.last_name ASC NULLS LAST, u.first_name ASC NULLS LAST, u.id ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<ClubMemberRow> findClubMemberPage(
            @Param("clubId") UUID clubId,
            @Param("pattern") String pattern,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Query(value = """
            SELECT count(*) FROM users u
            WHERE u.id IN (
            """ + ClubMembershipSql.MEMBER_IDS + """
            )
            AND
            """ + UserSearchSql.NAME_OR_EMAIL_MATCH,
            nativeQuery = true)
    long countUsersInClub(
            @Param("clubId") UUID clubId,
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
}
