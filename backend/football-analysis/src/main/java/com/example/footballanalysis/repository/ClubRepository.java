package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.Club;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

@Repository
public interface ClubRepository extends JpaRepository<Club, UUID> {
    @Query(
            value = "select club from Club club order by lower(club.name) asc, club.id asc",
            countQuery = "select count(club) from Club club")
    Page<Club> findPageOrderedByName(Pageable pageable);

    @Query(
            value = """
                    select club from Club club
                    where club.id in (
                        select membership.club.id from ClubAdminMembership membership
                        where membership.user.id = :userId
                    )
                    order by lower(club.name) asc, club.id asc
                    """,
            countQuery = """
                    select count(club) from Club club
                    where club.id in (
                        select membership.club.id from ClubAdminMembership membership
                        where membership.user.id = :userId
                    )
                    """)
    Page<Club> findPageAdministeredBy(@Param("userId") UUID userId, Pageable pageable);

    @Query(
            value = """
                    select club from Club club
                    where club.id in (
                        select membership.club.id from ClubAdminMembership membership
                        where membership.user.id = :userId
                    )
                    or club.id in (
                        select team.club.id from Team team
                        where team.id in :teamIds and team.club is not null
                    )
                    order by lower(club.name) asc, club.id asc
                    """,
            countQuery = """
                    select count(club) from Club club
                    where club.id in (
                        select membership.club.id from ClubAdminMembership membership
                        where membership.user.id = :userId
                    )
                    or club.id in (
                        select team.club.id from Team team
                        where team.id in :teamIds and team.club is not null
                    )
                    """)
    Page<Club> findPageForMember(
            @Param("userId") UUID userId,
            @Param("teamIds") Collection<UUID> teamIds,
            Pageable pageable);
}
