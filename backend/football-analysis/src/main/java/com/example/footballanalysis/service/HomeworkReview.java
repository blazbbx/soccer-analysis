package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.Homework;
import com.example.footballanalysis.model.db.HomeworkAssignment;
import com.example.footballanalysis.model.db.HomeworkAssignmentStatus;
import com.example.footballanalysis.model.db.HomeworkComment;
import com.example.footballanalysis.model.db.HomeworkStatus;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.requests.ReturnAssignmentRequest;
import com.example.footballanalysis.model.requests.ReviewAssignmentRequest;
import com.example.footballanalysis.model.responses.AssignmentResponse;
import com.example.footballanalysis.repository.HomeworkCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.example.footballanalysis.service.HomeworkGuard.blankToNull;
import static com.example.footballanalysis.service.HomeworkGuard.closed;
import static com.example.footballanalysis.service.HomeworkGuard.invalidTransition;
import static com.example.footballanalysis.service.HomeworkGuard.logAssignment;

/**
 * The coach's decision on submitted work: accept it, with an optional note, or send it back for a fix with a required one.
 * An acceptance is final; work sent back returns to the player's to-do list and can be handed in again.
 */
@Service
@RequiredArgsConstructor
public class HomeworkReview {

    private final UserAccessService userAccessService;
    private final HomeworkAssignmentViews views;
    private final HomeworkGuard guard;
    private final HomeworkCommentRepository commentRepository;

    @Transactional
    public AssignmentResponse review(UUID assignmentId, ReviewAssignmentRequest request, User actor) {
        HomeworkGuard.Locked locked = requireDecision(assignmentId, actor, "An archived homework cannot be reviewed.");
        HomeworkAssignment assignment = locked.assignment();
        Homework homework = locked.homework();
        LocalDateTime now = guard.now();
        assignment.setStatus(HomeworkAssignmentStatus.COMPLETED);
        assignment.setReviewedAt(now);
        assignment.setReviewedBy(actor);
        assignment.setCoachFeedback(blankToNull(request.feedback()));
        assignment.setUpdatedAt(now);
        guard.auditAssignment(actor, "HOMEWORK_REVIEWED", assignment, "homeworkId=" + homework.getId() + ",profileId="
                + assignment.getPlayerProfile().getId());
        logAssignment("Reviewed homework", homework, assignment);
        return views.toResponse(assignment, true, actor);
    }

    /**
     * The answers stay, so the player fixes them instead of starting over. The note is kept on the assignment for the
     * banner on the player's page, and goes into the conversation too, so every round stays readable there.
     */
    @Transactional
    public AssignmentResponse returnForRevision(UUID assignmentId, ReturnAssignmentRequest request, User actor) {
        HomeworkGuard.Locked locked = requireDecision(assignmentId, actor, "An archived homework cannot be sent back.");
        HomeworkAssignment assignment = locked.assignment();
        Homework homework = locked.homework();
        LocalDateTime now = guard.now();
        String note = request.note().trim();
        assignment.setStatus(HomeworkAssignmentStatus.RETURNED);
        assignment.setReturnedAt(now);
        assignment.setReturnedBy(actor);
        assignment.setReturnNote(note);
        assignment.setUpdatedAt(now);

        HomeworkComment comment = new HomeworkComment();
        comment.setAssignment(assignment);
        comment.setAuthor(actor);
        comment.setBody(note);
        comment.setCreatedAt(now);
        commentRepository.save(comment);

        guard.auditAssignment(actor, "HOMEWORK_RETURNED", assignment, "homeworkId=" + homework.getId() + ",profileId="
                + assignment.getPlayerProfile().getId());
        logAssignment("Returned homework for revision", homework, assignment);
        return views.toResponse(assignment, true, actor);
    }

    /** Under the row lock: a second coach deciding at the same time sees the first decision here and gets a 409. */
    private HomeworkGuard.Locked requireDecision(UUID assignmentId, User actor, String archivedMessage) {
        HomeworkGuard.Locked locked = guard.lock(assignmentId);
        Homework homework = locked.homework();
        userAccessService.requireHomeworkWrite(actor, homework.getTeam().getId());
        if (homework.getStatus() == HomeworkStatus.ARCHIVED) {
            throw closed(archivedMessage);
        }
        if (locked.assignment().getStatus() != HomeworkAssignmentStatus.SUBMITTED) {
            throw invalidTransition("Only submitted work can be decided on.");
        }
        return locked;
    }
}
