package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Homework;
import com.example.footballanalysis.model.db.HomeworkAssignment;
import com.example.footballanalysis.model.db.HomeworkAssignmentStatus;
import com.example.footballanalysis.model.db.HomeworkStatus;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.repository.HomeworkAssignmentRepository;
import com.example.footballanalysis.repository.HomeworkRepository;
import com.example.footballanalysis.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * What every homework service needs: the lookups and the locks, the rules each writer checks, the audit and the errors.
 * It runs inside the caller's transaction and never starts one.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HomeworkGuard {

    /** Work handed in: the items it answered may no longer change. */
    static final Set<HomeworkAssignmentStatus> HANDED_IN = EnumSet.of(
            HomeworkAssignmentStatus.SUBMITTED,
            HomeworkAssignmentStatus.COMPLETED);

    /**
     * What is on the recipient's to-do list, and what they may still edit: not yet handed in, or sent back by the coach.
     * Only the first is subject to the deadline ({@link HomeworkDeadlines#OPEN}).
     */
    static final Set<HomeworkAssignmentStatus> TODO = EnumSet.of(
            HomeworkAssignmentStatus.ASSIGNED,
            HomeworkAssignmentStatus.RETURNED);

    private static final String HOMEWORK_TARGET = "HOMEWORK";
    private static final String ASSIGNMENT_TARGET = "HOMEWORK_ASSIGNMENT";

    private final HomeworkRepository homeworkRepository;
    private final HomeworkAssignmentRepository assignmentRepository;
    private final TeamRepository teamRepository;
    private final UserAccessService userAccessService;
    private final AuditEventService auditEventService;
    private final Clock clock;

    public LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    // Lookups and locks

    public Team requireTeam(UUID teamId) {
        return teamRepository.findById(teamId).orElseThrow(() -> teamNotFound(teamId));
    }

    public void lockTeam(UUID teamId) {
        teamRepository.findByIdForUpdate(teamId).orElseThrow(() -> teamNotFound(teamId));
    }

    public Homework requireHomework(UUID teamId, UUID homeworkId) {
        return homeworkRepository.findById(homeworkId)
                .filter(homework -> teamId.equals(homework.getTeam().getId()))
                .orElseThrow(() -> homeworkNotFound(homeworkId));
    }

    public Homework lockHomework(UUID teamId, UUID homeworkId) {
        return homeworkRepository.findByIdForUpdate(homeworkId)
                .filter(homework -> teamId.equals(homework.getTeam().getId()))
                .orElseThrow(() -> homeworkNotFound(homeworkId));
    }

    /** Always the homework first and the assignment second, so the writers of an assignment cannot deadlock each other. */
    public Locked lock(UUID assignmentId) {
        UUID homeworkId = assignmentRepository.findHomeworkIdById(assignmentId)
                .orElseThrow(() -> assignmentNotFound(assignmentId));
        Homework homework = homeworkRepository.findByIdForShare(homeworkId)
                .orElseThrow(() -> assignmentNotFound(assignmentId));
        HomeworkAssignment assignment = assignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> assignmentNotFound(assignmentId));
        return new Locked(homework, assignment);
    }

    public HomeworkAssignment requireAssignment(UUID assignmentId) {
        return assignmentRepository.findDetailById(assignmentId).orElseThrow(() -> assignmentNotFound(assignmentId));
    }

    // Rules

    /** Who may decide on the team's homework. */
    public boolean canManage(User actor, Homework homework) {
        return userAccessService.isTeamStaff(actor, homework.getTeam());
    }

    public void requireWritable(Homework homework, HomeworkAssignment assignment, LocalDateTime now) {
        if (homework.getStatus() != HomeworkStatus.PUBLISHED) {
            throw closed("This homework is closed.");
        }
        if (HomeworkDeadlines.lateBlocked(homework, assignment, now)) {
            throw closed("The deadline has passed and late work is not accepted.");
        }
    }

    public static void requireNotArchived(Homework homework) {
        if (homework.getStatus() == HomeworkStatus.ARCHIVED) {
            throw closed("This homework is closed.");
        }
    }

    // Audit and log

    public void auditHomework(User actor, String eventType, UUID homeworkId, String details) {
        record(actor, eventType, HOMEWORK_TARGET, homeworkId, details);
    }

    public void auditAssignment(User actor, String eventType, HomeworkAssignment assignment, String details) {
        record(actor, eventType, ASSIGNMENT_TARGET, assignment.getId(), details);
    }

    private void record(User actor, String eventType, String target, UUID targetId, String details) {
        auditEventService.record(
                eventType,
                actor != null ? actor.getId() : null,
                target,
                targetId.toString(),
                details);
    }

    public static void logHomework(String message, UUID teamId, UUID homeworkId) {
        log.atInfo()
                .setMessage(message)
                .addKeyValue("teamId", teamId)
                .addKeyValue("homeworkId", homeworkId)
                .log();
    }

    public static void logAssignment(String message, Homework homework, HomeworkAssignment assignment) {
        log.atInfo()
                .setMessage(message)
                .addKeyValue("homeworkId", homework.getId())
                .addKeyValue("assignmentId", assignment.getId())
                .log();
    }

    // Values and errors

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public static NotFoundException homeworkNotFound(UUID homeworkId) {
        return new NotFoundException("error.homework.not_found", new Object[]{homeworkId}, "Homework not found on this team.");
    }

    public static NotFoundException assignmentNotFound(UUID assignmentId) {
        return new NotFoundException("error.homework.assignment_not_found", new Object[]{assignmentId}, "Assignment not found.");
    }

    public static ConflictException invalidTransition(String fallback) {
        return new ConflictException("error.homework.invalid_transition", new Object[0], fallback);
    }

    public static ConflictException closed(String fallback) {
        return new ConflictException("error.homework.closed", new Object[0], fallback);
    }

    public static BadRequestException dueBeforePublish() {
        return new BadRequestException("error.homework.due_before_publish", new Object[0], "The deadline is before the publish time.");
    }

    private static NotFoundException teamNotFound(UUID teamId) {
        return new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found.");
    }

    /** An assignment and its homework, both under the locks taken by {@link #lock(UUID)}. */
    public record Locked(Homework homework, HomeworkAssignment assignment) {
    }
}
