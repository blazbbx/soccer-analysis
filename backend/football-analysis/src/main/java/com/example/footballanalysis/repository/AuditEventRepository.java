package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    /**
     * Whether this user wrote an event of this type on this target since the given time. {@code created_at} has no
     * zone: it is the JVM's local time when the row was written, so {@code since} must be in the same representation.
     */
    @Query("""
            select count(e) > 0 from AuditEvent e
            where e.eventType = :eventType and e.actorUserId = :actorUserId and e.targetId = :targetId and e.createdAt >= :since
            """)
    boolean existsByActorSince(@Param("eventType") String eventType,
                               @Param("actorUserId") UUID actorUserId,
                               @Param("targetId") String targetId,
                               @Param("since") LocalDateTime since);

    /**
     * Deletes the sport medical audit rows ({@code MEDICAL_} event types) older than the cutoff, in one statement.
     * {@code created_at} has no zone, so the cutoff is in the JVM's local time, as the rows were written.
     */
    @Modifying
    @Query("delete from AuditEvent e where e.createdAt < :cutoff and e.eventType like 'MEDICAL\\_%' escape '\\'")
    int deleteMedicalBefore(@Param("cutoff") LocalDateTime cutoff);
}