package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.AuditEvent;
import com.example.footballanalysis.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditEventService {

    private final AuditEventRepository auditEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String eventType,
                       UUID actorUserId,
                       String actorUserRole,
                       String targetType,
                       String targetId,
                       String details) {
        try {
            AuditEvent event = new AuditEvent();
            event.setEventType(eventType);
            event.setActorUserId(actorUserId);
            event.setActorUserRole(actorUserRole);
            event.setTargetType(targetType);
            event.setTargetId(targetId);
            event.setDetails(details);
            auditEventRepository.save(event);
        } catch (RuntimeException ex) {
            log.warn("Failed to persist audit event type={} targetType={} targetId={}", eventType, targetType, targetId, ex);
        }
    }
}