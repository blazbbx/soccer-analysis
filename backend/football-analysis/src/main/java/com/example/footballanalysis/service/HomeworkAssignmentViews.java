package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Homework;
import com.example.footballanalysis.model.db.HomeworkAssignment;
import com.example.footballanalysis.model.db.HomeworkAssignmentStatus;
import com.example.footballanalysis.model.db.HomeworkStatus;
import com.example.footballanalysis.model.db.PlayerParent;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.responses.AssignmentResponse;
import com.example.footballanalysis.model.responses.HomeworkAnswerResponse;
import com.example.footballanalysis.model.responses.HomeworkItemResponse;
import com.example.footballanalysis.model.responses.HomeworkSummaryCountsResponse;
import com.example.footballanalysis.model.responses.MyHomeworkResponse;
import com.example.footballanalysis.model.responses.PageResponse;
import com.example.footballanalysis.paging.PageQuery;
import com.example.footballanalysis.repository.ClubAdminMembershipRepository;
import com.example.footballanalysis.repository.HomeworkAnswerRepository;
import com.example.footballanalysis.repository.HomeworkAssignmentRepository;
import com.example.footballanalysis.repository.HomeworkItemRepository;
import com.example.footballanalysis.repository.PlayerParentRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import com.example.footballanalysis.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import static com.example.footballanalysis.service.HomeworkGuard.TODO;
import static com.example.footballanalysis.service.HomeworkGuard.assignmentNotFound;

/** What the recipient's pages and the coach's assignment view get back, and the counts on the menu and the tabs. */
@Service
@RequiredArgsConstructor
public class HomeworkAssignmentViews {

    private final HomeworkAssignmentRepository assignmentRepository;
    private final HomeworkItemRepository itemRepository;
    private final HomeworkAnswerRepository answerRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final PlayerParentRepository playerParentRepository;
    private final TeamRepository teamRepository;
    private final TeamMembershipQuery teamMembershipQuery;
    private final ClubAdminMembershipRepository clubAdminMembershipRepository;
    private final UserAccessService userAccessService;
    private final HomeworkAccess homeworkAccess;
    private final HomeworkGuard guard;

    @Transactional(readOnly = true)
    public PageResponse<MyHomeworkResponse> listMine(String tab, UUID profileId, Integer page, Integer size, User actor) {
        int pageNumber = PageQuery.page(page);
        int pageSize = PageQuery.size(size);
        List<UUID> profileIds = List.of(resolveProfile(profileId, actor));
        var pageable = PageQuery.of(pageNumber, pageSize);
        Page<HomeworkAssignment> found = switch (parseTab(tab)) {
            case TODO -> assignmentRepository.findTodoPage(profileIds, TODO, pageable);
            case SUBMITTED -> assignmentRepository.findSubmittedPage(profileIds, pageable);
            case DONE -> assignmentRepository.findDonePage(profileIds, pageable);
        };
        LocalDateTime now = guard.now();
        List<MyHomeworkResponse> items = found.stream().map(assignment -> toCard(assignment, now)).toList();
        return PageQuery.envelope(items, pageNumber, pageSize, found.getTotalElements());
    }

    /** The view chooses which number to count, never a right: without that hat every number is zero. */
    @Transactional(readOnly = true)
    public HomeworkSummaryCountsResponse summary(String view, User actor) {
        UserRole role = parseView(view, actor);
        if (role == null || !userAccessService.hasHat(actor, role)) {
            return HomeworkSummaryCountsResponse.empty();
        }
        return switch (role) {
            case PLAYER -> todoCounts(playerProfileRepository.findByLinkedUser_Id(actor.getId())
                    .map(profile -> List.of(profile.getId()))
                    .orElse(List.of()));
            case PARENT -> todoCounts(playerParentRepository.findByUser_Id(actor.getId()).stream()
                    .map(PlayerParent::getPlayerProfile)
                    .map(PlayerProfile::getId)
                    .toList());
            case COACH -> toReview(teamMembershipQuery.findCoachTeamIds(actor.getId()));
            case CLUB_ADMIN -> toReview(clubTeamIds(actor));
            default -> HomeworkSummaryCountsResponse.empty();
        };
    }

    @Transactional(readOnly = true)
    public AssignmentResponse get(UUID assignmentId, User actor) {
        HomeworkAssignment assignment = assignmentRepository.findDetailById(assignmentId)
                .orElseThrow(() -> assignmentNotFound(assignmentId));
        boolean manager = guard.canManage(actor, assignment.getHomework());
        if (!manager) {
            homeworkAccess.requireAssignee(actor, assignment);
        }
        return toResponse(assignment, manager, actor);
    }

    /** One assignment as the caller may see it. The writers return it too, inside their own transaction. */
    public AssignmentResponse toResponse(HomeworkAssignment assignment, boolean manager, User actor) {
        Homework homework = assignment.getHomework();
        PlayerProfile profile = assignment.getPlayerProfile();
        LocalDateTime now = guard.now();
        boolean lateBlocked = HomeworkDeadlines.lateBlocked(homework, assignment, now);
        boolean writable = !lateBlocked
                && homework.getStatus() == HomeworkStatus.PUBLISHED
                && TODO.contains(assignment.getStatus());
        boolean showCorrect = (manager && !homeworkAccess.isAssignee(actor, assignment))
                || assignment.getStatus() == HomeworkAssignmentStatus.COMPLETED;
        List<HomeworkItemResponse> items = itemRepository.findWithClipByHomeworkId(homework.getId()).stream()
                .map(item -> HomeworkViews.toItemResponse(item, showCorrect))
                .toList();
        List<HomeworkAnswerResponse> answers = answerRepository.findByAssignmentId(assignment.getId()).stream()
                .map(answer -> new HomeworkAnswerResponse(
                        answer.getId().getItemId(), answer.getTextAnswer(), answer.getSelectedOptions(), answer.getTimestampMs()))
                .toList();
        User submittedBy = assignment.getSubmittedBy();
        User reviewedBy = assignment.getReviewedBy();
        User returnedBy = assignment.getReturnedBy();
        User createdBy = homework.getCreatedBy();
        return new AssignmentResponse(
                assignment.getId(),
                homework.getId(),
                homework.getTeam().getId(),
                homework.getTeam().getName(),
                homework.getTitle(),
                homework.getDescription(),
                homework.getStatus(),
                homework.getUpdatedAt(),
                profile.getId(),
                profile.getFirstName(),
                profile.getLastName(),
                assignment.getStatus(),
                assignment.getResponseText(),
                homework.getDueAt(),
                homework.isAllowLate(),
                assignment.getFirstViewedAt(),
                assignment.getSubmittedAt(),
                submittedBy != null ? submittedBy.getFirstName() : null,
                submittedBy != null ? submittedBy.getLastName() : null,
                submittedBy != null ? !isLinkedUser(profile, submittedBy) : null,
                createdBy != null ? createdBy.getFirstName() : null,
                createdBy != null ? createdBy.getLastName() : null,
                assignment.getReviewedAt(),
                reviewedBy != null ? reviewedBy.getFirstName() : null,
                reviewedBy != null ? reviewedBy.getLastName() : null,
                assignment.getCoachFeedback(),
                assignment.getReturnedAt(),
                returnedBy != null ? returnedBy.getFirstName() : null,
                returnedBy != null ? returnedBy.getLastName() : null,
                assignment.getReturnNote(),
                HomeworkDeadlines.overdue(homework, assignment, now),
                HomeworkDeadlines.dueSoon(homework, assignment, now),
                HomeworkDeadlines.late(homework, assignment),
                lateBlocked,
                writable,
                items,
                answers);
    }

    private UUID resolveProfile(UUID profileId, User actor) {
        if (profileId != null) {
            PlayerProfile profile = playerProfileRepository.findById(profileId).orElseThrow(() -> new NotFoundException(
                    "error.player_profile.not_found",
                    new Object[]{profileId},
                    "Player profile not found."));
            if (!homeworkAccess.canActFor(actor, profile)) {
                throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied to this player's homework.");
            }
            return profileId;
        }
        if (!userAccessService.hasHat(actor, UserRole.PLAYER)) {
            throw profileRequired();
        }
        return playerProfileRepository.findByLinkedUser_Id(actor.getId())
                .map(PlayerProfile::getId)
                .orElseThrow(HomeworkAssignmentViews::profileRequired);
    }

    private HomeworkSummaryCountsResponse todoCounts(List<UUID> profileIds) {
        if (profileIds.isEmpty()) {
            return HomeworkSummaryCountsResponse.empty();
        }
        LocalDateTime now = guard.now();
        Object[] row = assignmentRepository.summarizeTodo(profileIds, TODO, now).get(0);
        return new HomeworkSummaryCountsResponse(
                ((Number) row[0]).longValue(),
                ((Number) row[1]).longValue(),
                0,
                (LocalDateTime) row[2]);
    }

    private HomeworkSummaryCountsResponse toReview(List<UUID> teamIds) {
        if (teamIds.isEmpty()) {
            return HomeworkSummaryCountsResponse.empty();
        }
        return new HomeworkSummaryCountsResponse(0, 0, assignmentRepository.countToReviewOnTeams(teamIds), null);
    }

    private List<UUID> clubTeamIds(User actor) {
        List<UUID> clubIds = clubAdminMembershipRepository.findClubIdsByUserId(actor.getId());
        return clubIds.isEmpty() ? List.of() : teamRepository.findIdsByClub_IdIn(clubIds);
    }

    private static UserRole parseView(String view, User actor) {
        if (view == null || view.isBlank()) {
            return actor == null ? null : actor.getRole();
        }
        try {
            return UserRole.valueOf(view.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Tab parseTab(String tab) {
        if (tab == null || tab.isBlank()) {
            return Tab.TODO;
        }
        try {
            return Tab.valueOf(tab.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("error.validation.invalid_request", new Object[]{"tab"}, "Invalid request: tab");
        }
    }

    private MyHomeworkResponse toCard(HomeworkAssignment assignment, LocalDateTime now) {
        Homework homework = assignment.getHomework();
        return new MyHomeworkResponse(
                assignment.getId(),
                homework.getId(),
                homework.getTeam().getId(),
                homework.getTeam().getName(),
                assignment.getPlayerProfile().getId(),
                homework.getTitle(),
                homework.getStatus(),
                assignment.getStatus(),
                homework.getDueAt(),
                assignment.getSubmittedAt(),
                HomeworkDeadlines.overdue(homework, assignment, now),
                HomeworkDeadlines.dueSoon(homework, assignment, now),
                HomeworkDeadlines.late(homework, assignment),
                HomeworkDeadlines.lateBlocked(homework, assignment, now));
    }

    private static boolean isLinkedUser(PlayerProfile profile, User user) {
        return profile.getLinkedUser() != null && Objects.equals(profile.getLinkedUser().getId(), user.getId());
    }

    private static BadRequestException profileRequired() {
        return new BadRequestException("validation.homework.profile_required", new Object[0], "Choose a player.");
    }

    private enum Tab {
        TODO, SUBMITTED, DONE
    }
}
