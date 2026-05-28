package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.CupTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
public interface CupTeamRepository extends JpaRepository<CupTeam, UUID> {

    List<CupTeam> findByCup_IdOrderByCreatedAt(UUID cupId);

    Stream<CupTeam> streamByCup_Id(UUID cupId);

    boolean existsByIdAndCup_Id(UUID teamId, UUID cupId);
}
