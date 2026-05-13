package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.TeamMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TeamMessageRepository extends JpaRepository<TeamMessage, UUID> {

    /**
     * Visszaadja az adott csapat legutolsó N üzenetét időrend szerint csökkentően sorrendben.
     * @param teamId a csapat azonosítója
     * @param pageable az oldalazási információ (limit)
     * @return az üzenetek listája csökkentő időrendben
     */
    @Query("SELECT tm FROM TeamMessage tm WHERE tm.teamId = :teamId ORDER BY tm.createdAt DESC, tm.id DESC")
    List<TeamMessage> findLatestMessagesByTeamId(@Param("teamId") UUID teamId, Pageable pageable);

    @Query("""
            SELECT tm
            FROM TeamMessage tm
            WHERE tm.teamId = :teamId
              AND tm.createdAt < :beforeCreatedAt
            ORDER BY tm.createdAt DESC, tm.id DESC
            """)
    List<TeamMessage> findMessagesBefore(
            @Param("teamId") UUID teamId,
            @Param("beforeCreatedAt") LocalDateTime beforeCreatedAt,
            Pageable pageable
    );

}
