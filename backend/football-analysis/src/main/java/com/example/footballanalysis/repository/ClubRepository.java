package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.Club;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClubRepository extends JpaRepository<Club, UUID> {

    /** The club, locked until the transaction ends: two agreement recordings for one club run one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select club from Club club where club.id = :id")
    Optional<Club> findByIdForUpdate(@Param("id") UUID id);

    /** The longest history retention any club chose, or null without clubs. */
    @Query("select max(c.medicalHistoryRetentionYears) from Club c")
    Integer findMaxMedicalHistoryRetentionYears();
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
