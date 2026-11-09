package com.example.footballanalysis.model.responses;

import com.example.footballanalysis.model.db.HomeworkAssignmentStatus;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** One row of the coach's recipient table. */
public record AssignmentRowResponse(
        UUID id,
        UUID playerProfileId,
        String firstName,
        String lastName,
        HomeworkAssignmentStatus status,
        LocalDateTime effectiveDueAt,
        LocalDateTime firstViewedAt,
        LocalDateTime submittedAt,
        String submittedByFirstName,
        String submittedByLastName,
        Boolean submittedByParent,
        LocalDateTime reviewedAt,
        boolean overdue,
        boolean late,
        boolean dueSoon,
        Map<UUID, Integer> timestamps
) {}
