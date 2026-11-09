package com.example.footballanalysis.model.responses;

import com.example.footballanalysis.model.db.HomeworkAssignmentStatus;
import com.example.footballanalysis.model.db.HomeworkStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One assignment. For a recipient the items carry the correct options only once the assignment is completed, and nothing
 * here names another recipient. {@code writable} says whether the recipient may save and hand in now.
 */
public record AssignmentResponse(
        UUID id,
        UUID homeworkId,
        UUID teamId,
        String teamName,
        String title,
        String description,
        HomeworkStatus homeworkStatus,
        LocalDateTime homeworkUpdatedAt,     // sent back with the hand-in, so answers to changed questions are refused
        UUID playerProfileId,
        String firstName,
        String lastName,
        HomeworkAssignmentStatus status,
        String responseText,
        LocalDateTime effectiveDueAt,
        boolean allowLate,
        LocalDateTime firstViewedAt,
        LocalDateTime submittedAt,
        String submittedByFirstName,
        String submittedByLastName,
        Boolean submittedByParent,
        String createdByFirstName,
        String createdByLastName,
        LocalDateTime reviewedAt,
        String reviewedByFirstName,
        String reviewedByLastName,
        String coachFeedback,
        LocalDateTime returnedAt,            // the last time the coach sent the work back for a fix
        String returnedByFirstName,
        String returnedByLastName,
        String returnNote,
        boolean overdue,
        boolean dueSoon,
        boolean late,
        boolean lateBlocked,
        boolean writable,
        List<HomeworkItemResponse> items,
        List<HomeworkAnswerResponse> answers
) {}
