package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.CupMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
public interface CupMatchRepository extends JpaRepository<CupMatch, UUID> {

    List<CupMatch> findByCup_IdOrderByScheduledAt(UUID cupId);

    Stream<CupMatch> streamByCup_IdAndPlayedTrue(UUID cupId);

    Optional<CupMatch> findByIdAndCup_Id(UUID matchId, UUID cupId);

    boolean existsByIdAndCup_Id(UUID matchId, UUID cupId);

    boolean existsByCup_IdAndHomeTeam_IdAndAwayTeam_Id(UUID cupId, UUID homeTeamId, UUID awayTeamId);

    // Check if a given team is referenced as home in any match of the cup
    boolean existsByCup_IdAndHomeTeam_Id(UUID cupId, UUID homeTeamId);

    // Check if a given team is referenced as away in any match of the cup
    boolean existsByCup_IdAndAwayTeam_Id(UUID cupId, UUID awayTeamId);

    // Find a match by cup and teams (useful for update duplicate check)
    Optional<CupMatch> findByCup_IdAndHomeTeam_IdAndAwayTeam_Id(UUID cupId, UUID homeTeamId, UUID awayTeamId);

    @Query("select count(cm) from CupMatch cm where cm.cup.id = :cupId and (cm.homeTeam.id = :teamId or cm.awayTeam.id = :teamId)")
    long countByCupIdAndTeamId(@Param("cupId") UUID cupId, @Param("teamId") UUID teamId);
}
