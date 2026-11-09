package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.Homework;
import com.example.footballanalysis.model.db.HomeworkAssignment;
import com.example.footballanalysis.model.db.HomeworkComment;
import com.example.footballanalysis.model.db.HomeworkStatus;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.requests.CommentRequest;
import com.example.footballanalysis.model.responses.HomeworkCommentResponse;
import com.example.footballanalysis.repository.HomeworkAssignmentRepository;
import com.example.footballanalysis.repository.HomeworkCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.example.footballanalysis.service.HomeworkGuard.assignmentNotFound;
import static com.example.footballanalysis.service.HomeworkGuard.closed;

/** The conversation on one assignment, between the coach and the player's side. */
@Service
@RequiredArgsConstructor
public class HomeworkComments {

    private final HomeworkAssignmentRepository assignmentRepository;
    private final HomeworkCommentRepository commentRepository;
    private final HomeworkAccess homeworkAccess;
    private final HomeworkGuard guard;

    @Transactional(readOnly = true)
    public List<HomeworkCommentResponse> listComments(UUID assignmentId, User actor) {
        HomeworkAssignment assignment = assignmentRepository.findDetailById(assignmentId)
                .orElseThrow(() -> assignmentNotFound(assignmentId));
        if (!guard.canManage(actor, assignment.getHomework())) {
            homeworkAccess.requireAssignee(actor, assignment);
        }
        return commentRepository.findByAssignment_IdOrderByCreatedAtAscIdAsc(assignmentId).stream()
                .map(comment -> toResponse(comment, actor))
                .toList();
    }

    @Transactional
    public HomeworkCommentResponse addComment(UUID assignmentId, CommentRequest request, User actor) {
        // Under the homework lock, so an archive at the same moment cannot slip a message into a closed conversation.
        HomeworkGuard.Locked locked = guard.lock(assignmentId);
        HomeworkAssignment assignment = locked.assignment();
        Homework homework = locked.homework();
        boolean manager = guard.canManage(actor, homework);
        if (!manager) {
            homeworkAccess.requireAssignee(actor, assignment);
        }
        if (homework.getStatus() == HomeworkStatus.ARCHIVED) {
            throw closed("This conversation is closed.");
        }
        HomeworkComment comment = new HomeworkComment();
        comment.setAssignment(assignment);
        comment.setAuthor(actor);
        comment.setBody(request.body().trim());
        LocalDateTime now = guard.now();
        comment.setCreatedAt(now);
        commentRepository.save(comment);
        return toResponse(comment, actor);
    }

    private static HomeworkCommentResponse toResponse(HomeworkComment comment, User actor) {
        User author = comment.getAuthor();
        return new HomeworkCommentResponse(
                comment.getId(),
                author != null ? author.getFirstName() : null,
                author != null ? author.getLastName() : null,
                comment.getBody(),
                comment.getCreatedAt(),
                author != null && actor != null && Objects.equals(author.getId(), actor.getId()));
    }
}
