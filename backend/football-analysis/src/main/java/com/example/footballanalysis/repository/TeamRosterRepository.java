package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.MedicalMode;
import com.example.footballanalysis.model.db.TeamRoster;
import com.example.footballanalysis.model.db.TeamRosterId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TeamRosterRepository extends JpaRepository<TeamRoster, TeamRosterId> {

    @Query("""
            select roster.team.id, count(roster)
            from TeamRoster roster
            where roster.team.id in :teamIds
            group by roster.team.id
            """)
    List<Object[]> countByTeamIds(@Param("teamIds") Collection<UUID> teamIds);

    boolean existsByPlayerProfile_IdAndTeam_Id(UUID playerProfileId, UUID teamId);

    boolean existsByPlayerProfile_IdAndTeam_IdNot(UUID playerProfileId, UUID teamId);

    @Modifying
    @Query("delete from TeamRoster roster where roster.team.id = :teamId")
    int deleteByTeam_Id(@Param("teamId") UUID teamId);

    @Modifying
    @Query("delete from TeamRoster roster where roster.playerProfile.id = :profileId")
    int deleteByPlayerProfile_Id(@Param("profileId") UUID profileId);

    /** Roster rows of these profiles with their team and club, for the sport medical requirement. */
    @Query("""
            select roster from TeamRoster roster
            join fetch roster.team team
            left join fetch team.club
            where roster.playerProfile.id in :profileIds
            """)
    List<TeamRoster> findWithTeamAndClubByProfileIdIn(@Param("profileIds") Collection<UUID> profileIds);

    /**
     * Profile id, club retention, club id, team evidence and club evidence of each roster row whose team is effectively
     * ON. Plain values, so a Team or Club entity this transaction already holds cannot hide a switch another
     * transaction has committed since.
     */
    @Query("""
            select roster.playerProfile.id, club.medicalHistoryRetentionYears, club.id, team.medicalEvidence, club.medicalEvidence
            from TeamRoster roster
            join roster.team team
            left join team.club club
            where roster.playerProfile.id in :profileIds
              and (team.medicalMode = :on or (team.medicalMode = :inherit and club.medicalMode = :on))
            """)
    List<Object[]> findRequiringRetentionByProfileIdIn(
            @Param("profileIds") Collection<UUID> profileIds,
            @Param("on") MedicalMode on,
            @Param("inherit") MedicalMode inherit);

    @Query("select roster.playerProfile.id from TeamRoster roster where roster.team.id = :teamId")
    List<UUID> findProfileIdsByTeamId(@Param("teamId") UUID teamId);

    @Query("select distinct roster.playerProfile.id from TeamRoster roster where roster.team.club.id = :clubId")
    List<UUID> findProfileIdsByClubId(@Param("clubId") UUID clubId);

    @Query("""
            select roster.playerProfile.id
            from TeamRoster roster
            where roster.team.id = :teamId and roster.playerProfile.id in :profileIds
            """)
    List<UUID> findProfileIdsOnTeam(@Param("teamId") UUID teamId, @Param("profileIds") Collection<UUID> profileIds);
}
