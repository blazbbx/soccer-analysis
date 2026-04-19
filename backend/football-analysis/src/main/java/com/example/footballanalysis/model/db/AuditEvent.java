package com.example.footballanalysis.model.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "audit_events",
        indexes = {
                @Index(name = "idx_audit_events_event_type", columnList = "event_type"),
                @Index(name = "idx_audit_events_target", columnList = "target_type,target_id"),
                @Index(name = "idx_audit_events_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_user_role", length = 20)
    private String actorUserRole;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            String traceId = MDC.get("traceId");
            if (traceId != null && !traceId.isBlank()) {
                try {
                    id = UUID.fromString(traceId);
                } catch (IllegalArgumentException e) {
                    id = UUID.randomUUID();
                }
            } else {
                id = UUID.randomUUID();
            }
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}