package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.Homework;
import com.example.footballanalysis.model.db.HomeworkAssignment;
import com.example.footballanalysis.model.db.HomeworkAssignmentStatus;
import com.example.footballanalysis.model.db.HomeworkStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

/** The computed deadline flags. Lateness is never a stored status, because a submitted assignment can also be late. */
final class HomeworkDeadlines {

    /** Not yet handed in. Work sent back for a fix is not here: the deadline no longer applies to it. */
    static final Set<HomeworkAssignmentStatus> OPEN = EnumSet.of(HomeworkAssignmentStatus.ASSIGNED);

    private static final Duration DUE_SOON = Duration.ofHours(24);

    private HomeworkDeadlines() {
    }

    /** On an archived homework an unsubmitted assignment is closed, not overdue. */
    static boolean overdue(Homework homework, HomeworkAssignment assignment, LocalDateTime now) {
        LocalDateTime due = homework.getDueAt();
        return homework.getStatus() == HomeworkStatus.PUBLISHED
                && OPEN.contains(assignment.getStatus())
                && due != null
                && now.isAfter(due);
    }

    /**
     * Whether the hand-in came after the deadline. A fix of work the coach sent back after the deadline is never late:
     * the player could not have handed it in sooner.
     */
    static boolean late(Homework homework, HomeworkAssignment assignment) {
        LocalDateTime due = homework.getDueAt();
        LocalDateTime returned = assignment.getReturnedAt();
        return assignment.getSubmittedAt() != null
                && due != null
                && assignment.getSubmittedAt().isAfter(due)
                && (returned == null || !returned.isAfter(due));
    }

    /** Past the deadline with late work not allowed. */
    static boolean lateBlocked(Homework homework, HomeworkAssignment assignment, LocalDateTime now) {
        return !homework.isAllowLate() && overdue(homework, assignment, now);
    }

    static boolean dueSoon(Homework homework, HomeworkAssignment assignment, LocalDateTime now) {
        LocalDateTime due = homework.getDueAt();
        return homework.getStatus() == HomeworkStatus.PUBLISHED
                && OPEN.contains(assignment.getStatus())
                && due != null
                && !now.isAfter(due)
                && Duration.between(now, due).compareTo(DUE_SOON) < 0;
    }
}
