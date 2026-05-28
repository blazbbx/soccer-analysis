package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.Cup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CupRepository extends JpaRepository<Cup, UUID> {

    List<Cup> findByCreatedBy_IdOrderByCreatedAtDesc(UUID createdById);

    boolean existsByIdAndCreatedBy_Id(UUID cupId, UUID createdById);

    Optional<Cup> findById(UUID id);
}
