package com.example.footballanalysis.repository;

import aj.org.objectweb.asm.commons.Remapper;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Fan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FanRepository extends JpaRepository<Fan, UUID> {
    Optional<Fan> findByEmail(String email);
    List<Fan> findAllByTeams_Id(UUID teamId);

    @Modifying
    @Query(value = "DELETE FROM fan_favorite_teams WHERE team_id = :teamId", nativeQuery = true)
    void removeAllFansFromTeam(@Param("teamId") UUID teamId);
}
