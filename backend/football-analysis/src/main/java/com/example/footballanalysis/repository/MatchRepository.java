package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchRepository extends JpaRepository<Match, UUID> {

    // Spring Boot magically writes the SQL query for this just based on the method name!
    Optional<Match> findBySavedMinioFileName(String savedMinioFileName);

    List<Match> findAllByHomeTeam_IdOrAwayTeam_Id(UUID homeTeamId, UUID awayTeamId);
}