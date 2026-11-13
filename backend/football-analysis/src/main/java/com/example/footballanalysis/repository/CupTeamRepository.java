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

    Stream<CupTeam> streamByCup_Id(UUID cupId);

    boolean existsByIdAndCup_Id(UUID teamId, UUID cupId);

    /** Teams of the cup with the same name, ignoring case and surrounding spaces. */
    @Query("select t from CupTeam t where t.cup.id = :cupId and lower(trim(t.name)) = lower(trim(:name))")
    List<CupTeam> findSameName(@Param("cupId") UUID cupId, @Param("name") String name);

    List<CupTeam> findByCup_IdAndRealTeam_Id(UUID cupId, UUID realTeamId);

    @Modifying(flushAutomatically = true)
    @Query("update CupTeam c set c.realTeam = null where c.realTeam.id = :teamId")
    int clearRealTeamByTeamId(@Param("teamId") UUID teamId);
}
