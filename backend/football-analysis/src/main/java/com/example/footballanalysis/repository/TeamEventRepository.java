package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.TeamEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamEventRepository extends JpaRepository<TeamEvent, UUID> {

    @Query("""
            select event from TeamEvent event
            where event.team.id = :teamId and event.startsAt >= :from
            order by event.startsAt asc, event.id asc
            """)
    List<TeamEvent> findUpcoming(@Param("teamId") UUID teamId, @Param("from") LocalDateTime from);

    @Query("""
            select event from TeamEvent event
            where event.team.id = :teamId and event.startsAt < :before
            order by event.startsAt desc, event.id desc
            """)
    List<TeamEvent> findPastBefore(
            @Param("teamId") UUID teamId,
            @Param("before") LocalDateTime before,
            Pageable pageable);

    /** Keyset page: events older than the given one, same order as {@link #findPastBefore}. */
    @Query("""
            select event from TeamEvent event
            where event.team.id = :teamId
              and (event.startsAt < :startsAt or (event.startsAt = :startsAt and event.id < :id))
            order by event.startsAt desc, event.id desc
            """)
    List<TeamEvent> findPastBeforeEvent(
            @Param("teamId") UUID teamId,
            @Param("startsAt") LocalDateTime startsAt,
            @Param("id") UUID id,
            Pageable pageable);

    Optional<TeamEvent> findByIdAndTeam_Id(UUID id, UUID teamId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from TeamEvent event where event.id = :id")
    Optional<TeamEvent> findByIdForUpdate(@Param("id") UUID id);
}
