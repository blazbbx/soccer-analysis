package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.TeamEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamEventRepository extends JpaRepository<TeamEvent, UUID> {

    List<TeamEvent> findByTeam_IdOrderByStartsAtAscIdAsc(UUID teamId);

    Optional<TeamEvent> findByIdAndTeam_Id(UUID id, UUID teamId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from TeamEvent event where event.id = :id")
    Optional<TeamEvent> findByIdForUpdate(@Param("id") UUID id);
}
