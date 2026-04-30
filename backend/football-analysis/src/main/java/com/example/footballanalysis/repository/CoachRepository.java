package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.user.Coach;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CoachRepository extends JpaRepository<Coach, UUID> {
	Optional<Coach> findByEmail(String email);

    @Modifying
    @Query(value = "DELETE FROM coach_teams WHERE team_id = :teamId", nativeQuery = true)
    void removeAllCoachesFromTeam(@Param("teamId") UUID teamId);

    boolean existsByIdAndTeams_IdIn(UUID coachId, Collection<UUID> teamIds);
}

