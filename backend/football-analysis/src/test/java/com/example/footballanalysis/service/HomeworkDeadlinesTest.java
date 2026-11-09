package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.Homework;
import com.example.footballanalysis.model.db.HomeworkAssignment;
import com.example.footballanalysis.model.db.HomeworkAssignmentStatus;
import com.example.footballanalysis.model.db.HomeworkStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class HomeworkDeadlinesTest {

    private static final LocalDateTime DUE = LocalDateTime.of(2026, 11, 10, 18, 0);

    @Test
    void anOpenAssignmentIsOverdueOnlyAfterTheDeadlineOnAPublishedHomework() {
        Homework homework = homework(HomeworkStatus.PUBLISHED, DUE);
        HomeworkAssignment open = assignment(HomeworkAssignmentStatus.ASSIGNED);

        assertThat(HomeworkDeadlines.overdue(homework, open, DUE)).isFalse();
        assertThat(HomeworkDeadlines.overdue(homework, open, DUE.plusSeconds(1))).isTrue();
        assertThat(HomeworkDeadlines.overdue(homework(HomeworkStatus.ARCHIVED, DUE), open, DUE.plusDays(1))).isFalse();
        assertThat(HomeworkDeadlines.overdue(homework(HomeworkStatus.PUBLISHED, null), open, DUE.plusDays(1))).isFalse();
    }

    @Test
    void aHandedInAssignmentIsNeverOverdue() {
        Homework homework = homework(HomeworkStatus.PUBLISHED, DUE);
        for (HomeworkAssignmentStatus status : new HomeworkAssignmentStatus[]{
                HomeworkAssignmentStatus.SUBMITTED, HomeworkAssignmentStatus.COMPLETED}) {
            assertThat(HomeworkDeadlines.overdue(homework, assignment(status), DUE.plusDays(1))).as(status.name()).isFalse();
        }
    }

    @Test
    void lateComesFromTheHandInTime() {
        Homework homework = homework(HomeworkStatus.PUBLISHED, DUE);
        HomeworkAssignment onTime = assignment(HomeworkAssignmentStatus.SUBMITTED);
        onTime.setSubmittedAt(DUE.minusHours(1));
        HomeworkAssignment late = assignment(HomeworkAssignmentStatus.SUBMITTED);
        late.setSubmittedAt(DUE.plusHours(1));

        assertThat(HomeworkDeadlines.late(homework, onTime)).isFalse();
        assertThat(HomeworkDeadlines.late(homework, late)).isTrue();
        assertThat(HomeworkDeadlines.late(homework, assignment(HomeworkAssignmentStatus.ASSIGNED))).isFalse();
    }

    @Test
    void dueSoonIsTheLast24HoursOfAnOpenAssignment() {
        Homework homework = homework(HomeworkStatus.PUBLISHED, DUE);
        HomeworkAssignment open = assignment(HomeworkAssignmentStatus.ASSIGNED);

        assertThat(HomeworkDeadlines.dueSoon(homework, open, DUE.minusHours(25))).isFalse();
        assertThat(HomeworkDeadlines.dueSoon(homework, open, DUE.minusHours(24))).isFalse();
        assertThat(HomeworkDeadlines.dueSoon(homework, open, DUE.minusHours(23))).isTrue();
        assertThat(HomeworkDeadlines.dueSoon(homework, open, DUE.plusSeconds(1))).isFalse();
        assertThat(HomeworkDeadlines.dueSoon(homework(HomeworkStatus.PUBLISHED, null), open, DUE)).isFalse();
        assertThat(HomeworkDeadlines.dueSoon(homework, assignment(HomeworkAssignmentStatus.SUBMITTED), DUE.minusHours(1))).isFalse();
    }

    private static Homework homework(HomeworkStatus status, LocalDateTime due) {
        Homework homework = new Homework();
        homework.setStatus(status);
        homework.setDueAt(due);
        return homework;
    }

    private static HomeworkAssignment assignment(HomeworkAssignmentStatus status) {
        HomeworkAssignment assignment = new HomeworkAssignment();
        assignment.setStatus(status);
        return assignment;
    }
}
