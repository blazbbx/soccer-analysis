package com.example.footballanalysis.model.responses;

import com.example.footballanalysis.model.db.HomeworkAssignmentStatus;
import com.example.footballanalysis.model.db.HomeworkStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One card of the recipient's list. {@code lateBlocked} is the past-deadline state where nothing can be handed in any more.
 */
public record MyHomeworkResponse(
        UUID assignmentId,
        UUID homeworkId,
        UUID teamId,
        String teamName,
        UUID playerProfileId,
        String title,
        HomeworkStatus homeworkStatus,
        HomeworkAssignmentStatus status,
        LocalDateTime effectiveDueAt,
        LocalDateTime submittedAt,
        boolean overdue,
        boolean dueSoon,
        boolean late,
        boolean lateBlocked
) {}
