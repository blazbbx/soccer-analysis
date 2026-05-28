package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
}