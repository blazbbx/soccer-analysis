package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.Cup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CupRepository extends JpaRepository<Cup, UUID> {

    List<Cup> findByCreatedBy_IdOrderByCreatedAtDesc(UUID createdById);

    List<Cup> findDistinctByCreatedBy_IdOrTeams_RealTeam_IdIn(UUID createdById, Collection<UUID> teamIds);

    boolean existsByIdAndCreatedBy_Id(UUID cupId, UUID createdById);

    Optional<Cup> findById(UUID id);
}
