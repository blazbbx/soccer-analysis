package com.example.footballanalysis.model.responses;

import com.example.footballanalysis.model.db.HomeworkStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One card of the coach's list. {@code submittedCount} is submitted and completed
 * together, {@code toReviewCount} is submitted only.
 */
public record HomeworkSummaryResponse(
        UUID id,
        UUID teamId,
        String title,
        HomeworkStatus status,
        LocalDateTime dueAt,
        boolean allowLate,
        LocalDateTime updatedAt,
        long assignedCount,
        long submittedCount,
        long completedCount,
        long overdueCount,
        long toReviewCount
) {}
