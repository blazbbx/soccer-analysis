package com.example.footballanalysis.repository;

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
}
