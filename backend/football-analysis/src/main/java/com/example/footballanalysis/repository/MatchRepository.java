package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.Match;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchRepository extends JpaRepository<Match, UUID> {

    @Override
    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    List<Match> findAll();

    @Override
    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    Optional<Match> findById(UUID id);

    // Spring Boot magically writes the SQL query for this just based on the method name!
    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    Optional<Match> findBySavedMinioFileName(String savedMinioFileName);

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    List<Match> findAllByHomeTeam_IdOrAwayTeam_Id(UUID homeTeamId, UUID awayTeamId);


    @Query("SELECT m.homeTeam.id, m.awayTeam.id FROM Match m WHERE m.id = :matchId")
    List<UUID> findTeamIdsByMatchId(@Param("matchId") UUID matchId);
}