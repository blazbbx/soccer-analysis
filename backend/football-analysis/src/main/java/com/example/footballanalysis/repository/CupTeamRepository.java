package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.CupTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
public interface CupTeamRepository extends JpaRepository<CupTeam, UUID> {

    List<CupTeam> findByCup_IdOrderByCreatedAt(UUID cupId);

    Stream<CupTeam> streamByCup_Id(UUID cupId);

    boolean existsByIdAndCup_Id(UUID teamId, UUID cupId);

    @Modifying(flushAutomatically = true)
    @Query("update CupTeam c set c.realTeam = null where c.realTeam.id = :teamId")
    int clearRealTeamByTeamId(@Param("teamId") UUID teamId);
}
