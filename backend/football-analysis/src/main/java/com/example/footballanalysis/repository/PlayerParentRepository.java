package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.PlayerParent;
import com.example.footballanalysis.model.db.PlayerParentId;
import com.example.footballanalysis.model.responses.ParentChildName;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface PlayerParentRepository extends JpaRepository<PlayerParent, PlayerParentId> {

    @EntityGraph(attributePaths = {"playerProfile", "playerProfile.linkedUser"})
    List<PlayerParent> findByUser_Id(UUID userId);

    boolean existsByPlayerProfile_Id(UUID playerProfileId);

    @Query("""
            select (count(roster) > 0) from TeamRoster roster
            where roster.team.id in :teamIds
              and roster.playerProfile.id in (
                  select link.playerProfile.id from PlayerParent link where link.user.id = :userId
              )
            """)
    boolean existsByUser_IdAndPlayerProfile_Team_IdIn(@Param("userId") UUID userId, @Param("teamIds") Collection<UUID> teamIds);

    @Query("""
            select distinct roster.team.id from TeamRoster roster
            where roster.playerProfile.id in (
                select link.playerProfile.id from PlayerParent link where link.user.id = :userId
            )
            """)
    List<UUID> findTeamIdsByUserId(@Param("userId") UUID userId);

    @Query("""
            select new com.example.footballanalysis.model.responses.ParentChildName(
                link.user.id, profile.firstName, profile.lastName)
            from PlayerParent link
            join link.playerProfile profile, TeamRoster roster
            where roster.playerProfile = profile
              and roster.team.id = :teamId
              and link.user.id in :userIds
            order by profile.firstName, profile.lastName
            """)
    List<ParentChildName> findChildrenOnTeam(@Param("userIds") Collection<UUID> userIds, @Param("teamId") UUID teamId);
}
