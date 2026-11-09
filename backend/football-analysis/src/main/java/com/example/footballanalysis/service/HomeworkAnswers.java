package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.model.db.Homework;
import com.example.footballanalysis.model.db.HomeworkAnswer;
import com.example.footballanalysis.model.db.HomeworkAnswerId;
import com.example.footballanalysis.model.db.HomeworkAssignment;
import com.example.footballanalysis.model.db.HomeworkAssignmentStatus;
import com.example.footballanalysis.model.db.HomeworkItem;
import com.example.footballanalysis.model.db.HomeworkQuestionKind;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.requests.HomeworkAnswerRequest;
import com.example.footballanalysis.model.requests.SubmitAssignmentRequest;
import com.example.footballanalysis.model.responses.AssignmentResponse;
import com.example.footballanalysis.repository.HomeworkAnswerRepository;
import com.example.footballanalysis.repository.HomeworkAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.example.footballanalysis.service.HomeworkGuard.blankToNull;
import static com.example.footballanalysis.service.HomeworkGuard.invalidTransition;
import static com.example.footballanalysis.service.HomeworkGuard.logAssignment;

/** The recipient's work: opening it and handing it in. Nothing is stored before the hand-in. */
@Service
@RequiredArgsConstructor
public class HomeworkAnswers {

    private final HomeworkAnswerRepository answerRepository;
    private final HomeworkAssignmentRepository assignmentRepository;
    private final HomeworkAccess homeworkAccess;
    private final HomeworkContent content;
    private final HomeworkAssignmentViews views;
    private final HomeworkGuard guard;

    /**
     * The recipient's first opening, which the coach's table shows. Only this call sets it, so a GET or a prefetch never
     * counts. No lock: it is one atomic statement.
     */
    @Transactional
    public void markViewed(UUID assignmentId, User actor) {
        HomeworkAssignment assignment = guard.requireAssignment(assignmentId);
        homeworkAccess.requireAssignee(actor, assignment);
        if (assignment.getFirstViewedAt() == null) {
            assignmentRepository.markFirstViewed(assignmentId, guard.now());
        }
    }

    /**
     * Parent and child may both hand in, so the row lock comes first: the second caller sees the new status and gets a 409.
     * A homework without questions may be handed in empty: then it only says the video was watched. Work the coach sent
     * back is handed in the same way, and the new hand-in replaces the old one.
     */
    @Transactional
    public AssignmentResponse submit(UUID assignmentId, SubmitAssignmentRequest request, User actor) {
        HomeworkGuard.Locked locked = guard.lock(assignmentId);
        HomeworkAssignment assignment = locked.assignment();
        Homework homework = locked.homework();
        homeworkAccess.requireAssignee(actor, assignment);
        LocalDateTime now = guard.now();
        guard.requireWritable(homework, assignment, now);
        if (!HomeworkGuard.TODO.contains(assignment.getStatus())) {
            throw invalidTransition("This assignment cannot be handed in now.");
        }
        requireCurrentQuestions(request.expectedUpdatedAt(), homework);

        Map<UUID, HomeworkItem> questions = content.questionsOf(homework.getId());
        Map<UUID, HomeworkAnswer> answers = new HashMap<>();
        if (request.answers() != null) {
            for (HomeworkAnswerRequest answerRequest : request.answers()) {
                HomeworkItem item = questions.get(answerRequest.itemId());
                if (item == null || answers.containsKey(answerRequest.itemId())) {
                    throw new BadRequestException("error.homework.invalid_item", new Object[0], "The answer is not for a question of this homework.");
                }
                HomeworkAnswer answer = toAnswer(assignment, item, answerRequest);
                if (answer != null) {
                    answers.put(item.getId(), answer);
                }
            }
        }
        String responseText = blankToNull(request.responseText());
        requireComplete(questions, answers, responseText);

        boolean resubmission = assignment.getStatus() == HomeworkAssignmentStatus.RETURNED;
        if (resubmission) {
            // The fix replaces the whole earlier hand-in, so an answer cleared now must not survive from last time.
            answerRepository.deleteByAssignmentId(assignment.getId());
        }
        answerRepository.saveAll(answers.values());
        assignment.setResponseText(responseText);
        assignment.setStatus(HomeworkAssignmentStatus.SUBMITTED);
        assignment.setSubmittedAt(now);
        assignment.setSubmittedBy(actor);
        assignment.setUpdatedAt(now);

        // From the rule that let them in, not from users.role.
        String role = homeworkAccess.isLinkedPlayer(actor, assignment.getPlayerProfile()) ? "PLAYER" : "PARENT";
        guard.auditAssignment(actor, "HOMEWORK_SUBMITTED", assignment, "homeworkId=" + homework.getId() + ",profileId="
                + assignment.getPlayerProfile().getId() + ",submittedByRole=" + role + ",resubmission=" + resubmission);
        logAssignment("Submitted homework", homework, assignment);
        return views.toResponse(assignment, false, actor);
    }

    /** Any edit of the homework moves its update time, so a page showing the old questions cannot hand in against the new ones. */
    private static void requireCurrentQuestions(LocalDateTime expected, Homework homework) {
        if (expected == null) {
            throw new BadRequestException(
                    "validation.homework.expected_updated_at",
                    new Object[0],
                    "The update time of the homework is required.");
        }
        if (!expected.equals(homework.getUpdatedAt())) {
            throw new ConflictException("error.homework.stale_write", new Object[0], "The homework changed since the page was loaded.");
        }
    }

    /** A question whose clip is gone cannot be answered, so it must not block the hand-in. */
    private static void requireComplete(Map<UUID, HomeworkItem> questions, Map<UUID, HomeworkAnswer> answers, String responseText) {
        if (!questions.isEmpty() && responseText == null && answers.isEmpty()) {
            throw missingAnswer();
        }
        for (HomeworkItem item : questions.values()) {
            if (item.isRequired() && !unanswerable(item) && !answers.containsKey(item.getId())) {
                throw missingAnswer();
            }
        }
    }

    private static boolean unanswerable(HomeworkItem item) {
        return item.getQuestionKind() == HomeworkQuestionKind.TIMESTAMP
                && (item.getRefItem() == null || item.getRefItem().getClip() == null);
    }

    /** A blank answer gives no row, so a row always means the question has an answer. */
    private static HomeworkAnswer toAnswer(HomeworkAssignment assignment, HomeworkItem item, HomeworkAnswerRequest request) {
        String text = null;
        List<Integer> selected = null;
        Integer timestamp = null;
        switch (item.getQuestionKind()) {
            case FREE_TEXT -> text = blankToNull(request.textAnswer());
            case SINGLE_CHOICE, MULTI_CHOICE -> selected = validSelection(item, request.selectedOptions());
            case TIMESTAMP -> timestamp = request.timestampMs();
        }
        if (text == null && selected == null && timestamp == null) {
            return null;
        }
        HomeworkAnswer answer = new HomeworkAnswer();
        answer.setId(new HomeworkAnswerId(assignment.getId(), item.getId()));
        answer.setAssignment(assignment);
        answer.setItem(item);
        answer.setTextAnswer(text);
        answer.setSelectedOptions(selected);
        answer.setTimestampMs(timestamp);
        return answer;
    }

    private static List<Integer> validSelection(HomeworkItem item, List<Integer> selected) {
        if (selected == null || selected.isEmpty()) {
            return null;
        }
        int optionCount = item.getOptions() == null ? 0 : item.getOptions().size();
        boolean outOfRange = selected.stream().anyMatch(index -> index == null || index < 0 || index >= optionCount);
        boolean tooMany = item.getQuestionKind() == HomeworkQuestionKind.SINGLE_CHOICE && selected.size() > 1;
        if (outOfRange || tooMany || new HashSet<>(selected).size() != selected.size()) {
            throw new BadRequestException("error.homework.invalid_option", new Object[0], "A selected option is not on the list.");
        }
        return selected.stream().sorted().toList();
    }

    private static BadRequestException missingAnswer() {
        return new BadRequestException("error.homework.required_answer_missing", new Object[0], "Answer the required questions first.");
    }
}
