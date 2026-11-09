package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.model.db.Homework;
import com.example.footballanalysis.model.db.HomeworkAssignmentStatus;
import com.example.footballanalysis.model.db.HomeworkItem;
import com.example.footballanalysis.model.db.HomeworkItemType;
import com.example.footballanalysis.model.db.HomeworkStatus;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.requests.PublishHomeworkRequest;
import com.example.footballanalysis.model.requests.UpsertHomeworkRequest;
import com.example.footballanalysis.model.responses.HomeworkDetailResponse;
import com.example.footballanalysis.model.responses.HomeworkSummaryResponse;
import com.example.footballanalysis.model.responses.PageResponse;
import com.example.footballanalysis.paging.PageQuery;
import com.example.footballanalysis.repository.HomeworkAssignmentRepository;
import com.example.footballanalysis.repository.HomeworkItemRepository;
import com.example.footballanalysis.repository.HomeworkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import static com.example.footballanalysis.service.HomeworkGuard.blankToNull;
import static com.example.footballanalysis.service.HomeworkGuard.dueBeforePublish;
import static com.example.footballanalysis.service.HomeworkGuard.homeworkNotFound;
import static com.example.footballanalysis.service.HomeworkGuard.invalidTransition;
import static com.example.footballanalysis.service.HomeworkGuard.logHomework;

/** The coach's side of homework: writing, publishing and the lifecycle of a homework. */
@Service
@RequiredArgsConstructor
public class HomeworkService {

    private final HomeworkRepository homeworkRepository;
    private final HomeworkItemRepository itemRepository;
    private final HomeworkAssignmentRepository assignmentRepository;
    private final UserAccessService userAccessService;
    private final HomeworkContent content;
    private final HomeworkRecipients recipients;
    private final HomeworkViews views;
    private final HomeworkGuard guard;

    @Transactional(readOnly = true)
    public PageResponse<HomeworkSummaryResponse> list(UUID teamId, String tab, Integer page, Integer size, User actor) {
        guard.requireTeam(teamId);
        userAccessService.requireHomeworkRead(actor, teamId);
        int pageNumber = PageQuery.page(page);
        int pageSize = PageQuery.size(size);
        Page<Homework> found = findTab(teamId, parseTab(tab), pageNumber, pageSize);
        return PageQuery.envelope(views.summaries(found.getContent(), teamId), pageNumber, pageSize, found.getTotalElements());
    }

    @Transactional(readOnly = true)
    public HomeworkDetailResponse get(UUID teamId, UUID homeworkId, User actor) {
        guard.requireTeam(teamId);
        userAccessService.requireHomeworkRead(actor, teamId);
        return views.detail(guard.requireHomework(teamId, homeworkId), teamId, null);
    }

    /** For a link that knows only the homework. */
    @Transactional(readOnly = true)
    public HomeworkDetailResponse get(UUID homeworkId, User actor) {
        Homework homework = homeworkRepository.findById(homeworkId).orElseThrow(() -> homeworkNotFound(homeworkId));
        UUID teamId = homework.getTeam().getId();
        userAccessService.requireHomeworkRead(actor, teamId);
        return views.detail(homework, teamId, null);
    }

    @Transactional
    public HomeworkDetailResponse create(UUID teamId, UpsertHomeworkRequest request, User actor) {
        Team team = guard.requireTeam(teamId);
        userAccessService.requireHomeworkWrite(actor, teamId);
        recipients.requireValidDraft(request.entireRoster(), request.playerProfileIds(), teamId);
        Homework homework = new Homework();
        homework.setTeam(team);
        homework.setCreatedBy(actor);
        homework.setStatus(HomeworkStatus.DRAFT);
        applyFields(homework, request);
        LocalDateTime now = guard.now();
        homework.setCreatedAt(now);
        homework.setUpdatedAt(now);
        homeworkRepository.save(homework);
        content.sync(homework, request.items(), List.of(), false, actor);
        recipients.syncDraft(homework, request.entireRoster(), request.playerProfileIds());
        guard.auditHomework(actor, "HOMEWORK_CREATED", homework.getId(), "teamId=" + teamId);
        logHomework("Created homework", teamId, homework.getId());
        return views.detail(homework, teamId, null);
    }

    @Transactional
    public HomeworkDetailResponse update(UUID teamId, UUID homeworkId, UpsertHomeworkRequest request, User actor) {
        guard.requireTeam(teamId);
        userAccessService.requireHomeworkWrite(actor, teamId);
        Homework homework = guard.lockHomework(teamId, homeworkId);
        HomeworkGuard.requireNotArchived(homework);
        requireExpectedUpdatedAt(request.expectedUpdatedAt(), homework.getUpdatedAt());

        boolean draft = homework.getStatus() == HomeworkStatus.DRAFT;
        if (!draft && request.items().isEmpty()) {
            throw itemsRequired();
        }
        if (!draft && request.dueAt() != null && request.dueAt().isBefore(homework.getPublishedAt())) {
            throw dueBeforePublish();
        }
        if (draft) {
            recipients.requireValidDraft(request.entireRoster(), request.playerProfileIds(), teamId);
        }

        List<String> changed = changedFields(homework, request);
        applyFields(homework, request);
        // Any hand-in locks the questions.
        boolean locked = !draft && assignmentRepository.existsByHomework_IdAndSubmittedAtIsNotNull(homeworkId);
        List<HomeworkItem> stored = itemRepository.findWithClipByHomeworkId(homeworkId);
        if (content.sync(homework, request.items(), stored, locked, actor)) {
            changed.add("items");
        }
        if (draft && recipients.syncDraft(homework, request.entireRoster(), request.playerProfileIds())) {
            changed.add("recipients");
        }
        if (changed.isEmpty()) {
            // A save without changes keeps the version, so the open hand-ins of the recipients do not go stale.
            return views.detail(homework, teamId, null);
        }
        homework.setUpdatedAt(guard.now());
        guard.auditHomework(actor, "HOMEWORK_UPDATED", homeworkId, "teamId=" + teamId + ",fields=" + String.join("|", changed));
        logHomework("Updated homework", teamId, homeworkId);
        return views.detail(homework, teamId, null);
    }

    @Transactional
    public void delete(UUID teamId, UUID homeworkId, User actor) {
        guard.requireTeam(teamId);
        userAccessService.requireHomeworkWrite(actor, teamId);
        Homework homework = guard.lockHomework(teamId, homeworkId);
        if (assignmentRepository.existsByHomework_IdAndSubmittedAtIsNotNull(homeworkId)) {
            throw new ConflictException(
                    "error.homework.not_draft",
                    new Object[0],
                    "A homework with submitted work cannot be deleted.");
        }
        homeworkRepository.delete(homework);
        guard.auditHomework(actor, "HOMEWORK_DELETED", homeworkId, "teamId=" + teamId);
        logHomework("Deleted homework", teamId, homeworkId);
    }

    /** Locks the team first, so a roster removal running at the same time either sees the new rows or wins and gets a 404 here. */
    @Transactional
    public HomeworkDetailResponse publish(UUID teamId, UUID homeworkId, PublishHomeworkRequest request, User actor) {
        guard.requireTeam(teamId);
        userAccessService.requireHomeworkWrite(actor, teamId);
        guard.lockTeam(teamId);
        Homework homework = guard.lockHomework(teamId, homeworkId);
        if (homework.getStatus() != HomeworkStatus.DRAFT) {
            throw new ConflictException("error.homework.not_draft", new Object[0], "Only a draft can be published.");
        }
        requireExpectedUpdatedAt(request.expectedUpdatedAt(), homework.getUpdatedAt());

        LocalDateTime now = guard.now();
        if (homework.getDueAt() != null && homework.getDueAt().isBefore(now)) {
            throw dueBeforePublish();
        }
        List<UUID> assignees = recipients.forPublish(request.entireRoster(), request.playerProfileIds(), teamId);
        if (assignees.isEmpty()) {
            throw new BadRequestException("error.homework.no_assignees", new Object[0], "The homework has no recipients.");
        }
        List<HomeworkItem> items = itemRepository.findWithClipByHomeworkId(homeworkId);
        if (items.isEmpty()) {
            throw itemsRequired();
        }
        for (HomeworkItem item : items) {
            if (item.getType() == HomeworkItemType.CLIP && item.getClip() == null) {
                throw new BadRequestException("error.homework.clip_missing", new Object[0], "A clip of this homework was deleted.");
            }
            HomeworkContent.requireClipReady(item.getClip());
        }

        homework.setStatus(HomeworkStatus.PUBLISHED);
        homework.setPublishedAt(now);
        homework.setDraftEntireRoster(false);
        homework.setUpdatedAt(now);
        recipients.clearDraft(homeworkId);
        recipients.assign(homework, assignees, now);
        guard.auditHomework(actor, "HOMEWORK_PUBLISHED", homeworkId,
                "teamId=" + teamId + ",assigneeCount=" + assignees.size());
        logHomework("Published homework", teamId, homeworkId);
        return views.detail(homework, teamId, null);
    }

    /** There is no way back from the archive, so no work waiting for a decision may be left behind. */
    @Transactional
    public HomeworkDetailResponse archive(UUID teamId, UUID homeworkId, User actor) {
        guard.requireTeam(teamId);
        userAccessService.requireHomeworkWrite(actor, teamId);
        Homework homework = guard.lockHomework(teamId, homeworkId);
        LocalDateTime now = guard.now();
        if (homework.getStatus() != HomeworkStatus.PUBLISHED) {
            throw invalidTransition("Only a published homework can be archived.");
        }
        if (assignmentRepository.existsByHomework_IdAndStatus(homeworkId, HomeworkAssignmentStatus.SUBMITTED)) {
            throw new ConflictException(
                    "error.homework.archive_blocked",
                    new Object[0],
                    "Decide on the submitted work first.");
        }
        homework.setStatus(HomeworkStatus.ARCHIVED);
        homework.setUpdatedAt(now);
        guard.auditHomework(actor, "HOMEWORK_ARCHIVED", homeworkId, "teamId=" + teamId);
        logHomework("Archived homework", teamId, homeworkId);
        return views.detail(homework, teamId, null);
    }

    /**
     * A new draft of the same team. Only within the team: the clips are already in its homework, so the copy opens nothing
     * to anyone who could not play it before.
     */
    @Transactional
    public HomeworkDetailResponse duplicate(UUID teamId, UUID homeworkId, User actor) {
        Team team = guard.requireTeam(teamId);
        userAccessService.requireHomeworkWrite(actor, teamId);
        Homework source = guard.requireHomework(teamId, homeworkId);

        List<HomeworkItem> sourceItems = itemRepository.findWithClipByHomeworkId(homeworkId);
        List<HomeworkItem> kept = sourceItems.stream().filter(item -> HomeworkContent.survivesCopy(item, sourceItems)).toList();

        Homework copy = new Homework();
        copy.setTeam(team);
        copy.setCreatedBy(actor);
        copy.setStatus(HomeworkStatus.DRAFT);
        copy.setTitle(source.getTitle());
        copy.setDescription(source.getDescription());
        copy.setDueAt(source.getDueAt());
        copy.setAllowLate(source.isAllowLate());
        // No names come along: the default is the whole roster.
        copy.setDraftEntireRoster(true);
        LocalDateTime now = guard.now();
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        homeworkRepository.save(copy);
        content.copy(copy, kept);

        guard.auditHomework(actor, "HOMEWORK_CREATED", copy.getId(), "teamId=" + teamId + ",copiedFrom=" + homeworkId);
        logHomework("Copied homework", teamId, copy.getId());
        return views.detail(copy, teamId, sourceItems.size() - kept.size());
    }

    private Page<Homework> findTab(UUID teamId, Tab tab, int page, int size) {
        return switch (tab) {
            case ACTIVE -> homeworkRepository.findByTeam_IdAndStatus(
                    teamId, HomeworkStatus.PUBLISHED, pageable(page, size, Sort.Order.asc("dueAt")));
            case DRAFT -> homeworkRepository.findByTeam_IdAndStatus(
                    teamId, HomeworkStatus.DRAFT, pageable(page, size, Sort.Order.desc("updatedAt")));
            case ARCHIVED -> homeworkRepository.findByTeam_IdAndStatus(
                    teamId, HomeworkStatus.ARCHIVED, pageable(page, size, Sort.Order.desc("updatedAt")));
        };
    }

    private static Pageable pageable(int page, int size, Sort.Order order) {
        return PageRequest.of(page - 1, size, Sort.by(order, Sort.Order.asc("id")));
    }

    private static Tab parseTab(String tab) {
        if (tab == null || tab.isBlank()) {
            return Tab.ACTIVE;
        }
        try {
            return Tab.valueOf(tab.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("error.validation.invalid_request", new Object[]{"tab"}, "Invalid request: tab");
        }
    }

    private static void applyFields(Homework homework, UpsertHomeworkRequest request) {
        homework.setTitle(request.title().trim());
        homework.setDescription(blankToNull(request.description()));
        homework.setDueAt(request.dueAt());
        homework.setAllowLate(request.allowLate() == null || request.allowLate());
    }

    /** The field names for the audit: no text goes there. */
    private static List<String> changedFields(Homework homework, UpsertHomeworkRequest request) {
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(homework.getTitle(), request.title().trim())) {
            changed.add("title");
        }
        if (!Objects.equals(homework.getDescription(), blankToNull(request.description()))) {
            changed.add("description");
        }
        if (!Objects.equals(homework.getDueAt(), request.dueAt())) {
            changed.add("dueAt");
        }
        if (homework.isAllowLate() != (request.allowLate() == null || request.allowLate())) {
            changed.add("allowLate");
        }
        return changed;
    }

    private static void requireExpectedUpdatedAt(LocalDateTime expected, LocalDateTime stored) {
        if (expected == null) {
            throw new BadRequestException(
                    "validation.homework.expected_updated_at",
                    new Object[0],
                    "The previous update time is required to save changes.");
        }
        if (!expected.equals(stored)) {
            throw new ConflictException("error.homework.stale_write", new Object[0], "The homework was updated by someone else.");
        }
    }

    private static BadRequestException itemsRequired() {
        return new BadRequestException("validation.homework.items", new Object[0], "The homework needs at least one item.");
    }

    private enum Tab {
        ACTIVE, DRAFT, ARCHIVED
    }
}
