package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.AuditEvent;
import com.example.footballanalysis.repository.AuditEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
@Slf4j
public class AuditEventService {

    private final AuditEventRepository auditEventRepository;
    private final TransactionTemplate requiresNew;

    public AuditEventService(AuditEventRepository auditEventRepository,
                             PlatformTransactionManager transactionManager) {
        this.auditEventRepository = auditEventRepository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Writes one audit row after the current transaction commits.
     * The actor's role is not recorded for now: the former users.role column held only the primary role at the last login,
     * so it could not say on what right the action was taken. The actor_user_role column is kept for the old rows.
     */
    public void record(String eventType,
                       UUID actorUserId,
                       String targetType,
                       String targetId,
                       String details) {
        Runnable write = () -> {
            try {
                requiresNew.executeWithoutResult(status ->
                        persist(eventType, actorUserId, targetType, targetId, details));
            } catch (RuntimeException ex) {
                log.warn("Failed to persist audit event type={} targetType={} targetId={}", eventType, targetType, targetId, ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    write.run();
                }
            });
            return;
        }
        write.run();
    }

    private void persist(String eventType,
                         UUID actorUserId,
                         String targetType,
                         String targetId,
                         String details) {
        AuditEvent event = new AuditEvent();
        event.setEventType(eventType);
        event.setActorUserId(actorUserId);
        event.setTargetType(targetType);
        event.setTargetId(targetId);
        event.setDetails(details);
        auditEventRepository.save(event);
    }
}
