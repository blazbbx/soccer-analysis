package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.CupMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CupMatchRepository extends JpaRepository<CupMatch, UUID> {

    List<CupMatch> findByCup_IdOrderByScheduledAt(UUID cupId);

    List<CupMatch> findByCup_IdAndPlayedTrue(UUID cupId);

    Optional<CupMatch> findByIdAndCup_Id(UUID matchId, UUID cupId);

    boolean existsByIdAndCup_Id(UUID matchId, UUID cupId);

    /** Every match of the pairing in the cup, in either direction (A–B and B–A are the same pairing). */
    @Query("""
            select cm from CupMatch cm
            where cm.cup.id = :cupId
              and ((cm.homeTeam.id = :teamA and cm.awayTeam.id = :teamB)
                or (cm.homeTeam.id = :teamB and cm.awayTeam.id = :teamA))
            """)
    List<CupMatch> findPairing(@Param("cupId") UUID cupId, @Param("teamA") UUID teamA, @Param("teamB") UUID teamB);

    boolean existsByCup_IdAndPlayedTrue(UUID cupId);

    /** Cup matches linked to an analysed match, with their cup. */
    @Query("select cm from CupMatch cm join fetch cm.cup where cm.realMatch.id = :matchId")
    List<CupMatch> findByRealMatchIdWithCup(@Param("matchId") UUID matchId);

    @Query("select count(cm) from CupMatch cm where cm.cup.id = :cupId and (cm.homeTeam.id = :teamId or cm.awayTeam.id = :teamId)")
    long countByCupIdAndTeamId(@Param("cupId") UUID cupId, @Param("teamId") UUID teamId);
}
