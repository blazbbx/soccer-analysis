package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.Team;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlayerProfileRepository extends JpaRepository<PlayerProfile, UUID> {

    Optional<PlayerProfile> findByLinkedUser_Id(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from PlayerProfile profile where profile.id = :id")
    Optional<PlayerProfile> findByIdForUpdate(@Param("id") UUID id);

    @Query("select roster.playerProfile from TeamRoster roster where roster.team.id = :teamId")
    List<PlayerProfile> findByTeam_Id(@Param("teamId") UUID teamId);

    @Query("""
            select (count(roster) > 0) from TeamRoster roster
            where roster.playerProfile.linkedUser.id = :userId and roster.team.id = :teamId
            """)
    boolean existsByLinkedUser_IdAndTeam_Id(@Param("userId") UUID userId, @Param("teamId") UUID teamId);

    @Query("""
            select (count(roster) > 0) from TeamRoster roster
            where roster.playerProfile.linkedUser.id = :userId and roster.team.id in :teamIds
            """)
    boolean existsByLinkedUser_IdAndTeam_IdIn(@Param("userId") UUID userId, @Param("teamIds") Collection<UUID> teamIds);

    @Query("select roster.team.id from TeamRoster roster where roster.playerProfile.linkedUser.id = :userId")
    List<UUID> findTeamIdsByLinkedUserId(@Param("userId") UUID userId);

    @Query("select roster.team from TeamRoster roster where roster.playerProfile.linkedUser.id = :userId order by roster.team.name")
    List<Team> findTeamsByLinkedUserId(@Param("userId") UUID userId);

    @Query("select roster.team from TeamRoster roster left join fetch roster.team.club where roster.playerProfile.id = :profileId order by roster.team.name")
    List<Team> findTeamsByProfileId(@Param("profileId") UUID profileId);
}
