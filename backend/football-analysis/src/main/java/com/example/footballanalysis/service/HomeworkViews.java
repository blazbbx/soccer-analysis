package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.Homework;
import com.example.footballanalysis.model.db.HomeworkAssignment;
import com.example.footballanalysis.model.db.HomeworkAssignmentStatus;
import com.example.footballanalysis.model.db.HomeworkItem;
import com.example.footballanalysis.model.db.HomeworkStatus;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.responses.AssignmentRowResponse;
import com.example.footballanalysis.model.responses.HomeworkDetailResponse;
import com.example.footballanalysis.model.responses.HomeworkItemResponse;
import com.example.footballanalysis.model.responses.HomeworkSummaryResponse;
import com.example.footballanalysis.repository.HomeworkAnswerRepository;
import com.example.footballanalysis.repository.HomeworkAssignmentRepository;
import com.example.footballanalysis.repository.HomeworkDraftRecipientRepository;
import com.example.footballanalysis.repository.HomeworkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** What the coach's pages get back: the list cards, the homework itself and the recipient rows. */
@Component
@RequiredArgsConstructor
public class HomeworkViews {

    private final HomeworkItemRepository itemRepository;
    private final HomeworkAssignmentRepository assignmentRepository;
    private final HomeworkDraftRecipientRepository draftRecipientRepository;
    private final HomeworkAnswerRepository answerRepository;
    private final HomeworkGuard guard;

    /** The cards of one list page, with the progress in one query for the whole page. */
    public List<HomeworkSummaryResponse> summaries(List<Homework> homeworks, UUID teamId) {
        Map<UUID, Progress> progress = loadProgress(homeworks);
        return homeworks.stream()
                .map(homework -> toSummary(homework, teamId, progress.getOrDefault(homework.getId(), new Progress())))
                .toList();
    }

    public HomeworkDetailResponse detail(Homework homework, UUID teamId, Integer omittedItemCount) {
        List<HomeworkItemResponse> items = itemRepository.findWithClipByHomeworkId(homework.getId()).stream()
                .map(item -> toItemResponse(item, true))
                .toList();
        List<UUID> draftRecipients = homework.getStatus() == HomeworkStatus.DRAFT
                ? draftRecipientRepository.findByHomework_Id(homework.getId()).stream()
                        .map(recipient -> recipient.getId().getPlayerProfileId())
                        .toList()
                : List.of();
        return new HomeworkDetailResponse(
                homework.getId(), teamId, homework.getTitle(), homework.getDescription(), homework.getStatus(),
                homework.getDueAt(), homework.isAllowLate(),
                homework.getCreatedAt(), homework.getUpdatedAt(),
                homework.isDraftEntireRoster(), draftRecipients, items, omittedItemCount);
    }

    public List<AssignmentRowResponse> assignmentRows(Homework homework) {
        LocalDateTime now = guard.now();
        Map<UUID, Map<UUID, Integer>> timestamps = new HashMap<>();
        for (Object[] row : answerRepository.findTimestamps(homework.getId())) {
            timestamps.computeIfAbsent((UUID) row[0], id -> new HashMap<>()).put((UUID) row[1], (Integer) row[2]);
        }
        return assignmentRepository.findRowsByHomeworkId(homework.getId()).stream()
                .map(assignment -> new AssignmentRowResponse(
                        assignment.getId(),
                        assignment.getPlayerProfile().getId(),
                        assignment.getPlayerProfile().getFirstName(),
                        assignment.getPlayerProfile().getLastName(),
                        assignment.getStatus(),
                        homework.getDueAt(),
                        assignment.getFirstViewedAt(),
                        assignment.getSubmittedAt(),
                        assignment.getSubmittedBy() != null ? assignment.getSubmittedBy().getFirstName() : null,
                        assignment.getSubmittedBy() != null ? assignment.getSubmittedBy().getLastName() : null,
                        submittedByParent(assignment),
                        assignment.getReviewedAt(),
                        HomeworkDeadlines.overdue(homework, assignment, now),
                        HomeworkDeadlines.late(homework, assignment),
                        HomeworkDeadlines.dueSoon(homework, assignment, now),
                        timestamps.getOrDefault(assignment.getId(), Map.of())))
                .toList();
    }

    private Map<UUID, Progress> loadProgress(List<Homework> homeworks) {
        if (homeworks.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = homeworks.stream().map(Homework::getId).toList();
        Map<UUID, Progress> progress = new HashMap<>();
        for (Object[] row : assignmentRepository.countProgress(ids, HomeworkDeadlines.OPEN, guard.now())) {
            progress.computeIfAbsent((UUID) row[0], id -> new Progress())
                    .add((HomeworkAssignmentStatus) row[1], ((Number) row[2]).longValue(), ((Number) row[3]).longValue());
        }
        return progress;
    }

    private static HomeworkSummaryResponse toSummary(Homework homework, UUID teamId, Progress progress) {
        return new HomeworkSummaryResponse(
                homework.getId(), teamId, homework.getTitle(), homework.getStatus(),
                homework.getDueAt(), homework.isAllowLate(),
                homework.getUpdatedAt(), progress.assigned, progress.submitted, progress.completed,
                progress.overdue, progress.toReview);
    }

    /** One item of a homework. The correct options only when the caller may see them: never the recipient before review. */
    static HomeworkItemResponse toItemResponse(HomeworkItem item, boolean showCorrect) {
        Clip clip = item.getClip();
        return new HomeworkItemResponse(
                item.getId(), item.getPosition(), item.getType(), item.getBody(),
                clip != null ? clip.getId() : null,
                clip != null ? clip.getName() : null,
                clip != null ? clip.getRenderStatus() : null,
                item.getFocusMs(), item.getQuestionKind(), item.getOptions(),
                showCorrect ? item.getCorrectOptions() : null,
                item.isRequired(),
                item.getRefItem() != null ? item.getRefItem().getId() : null);
    }

    /** Null before a hand-in; otherwise whether it was someone other than the player's own login. */
    private static Boolean submittedByParent(HomeworkAssignment assignment) {
        if (assignment.getSubmittedBy() == null) {
            return null;
        }
        User linked = assignment.getPlayerProfile().getLinkedUser();
        return linked == null || !Objects.equals(linked.getId(), assignment.getSubmittedBy().getId());
    }

    private static final class Progress {
        private long assigned;
        private long submitted;
        private long completed;
        private long overdue;
        private long toReview;

        private void add(HomeworkAssignmentStatus status, long count, long overdueCount) {
            overdue += overdueCount;
            assigned += count;
            if (HomeworkGuard.HANDED_IN.contains(status)) {
                submitted += count;
            }
            if (status == HomeworkAssignmentStatus.COMPLETED) {
                completed += count;
            }
            if (status == HomeworkAssignmentStatus.SUBMITTED) {
                toReview += count;
            }
        }
    }
}
