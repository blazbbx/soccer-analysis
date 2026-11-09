package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Homework;
import com.example.footballanalysis.model.db.HomeworkAssignment;
import com.example.footballanalysis.model.db.HomeworkAssignmentStatus;
import com.example.footballanalysis.model.db.HomeworkDraftRecipient;
import com.example.footballanalysis.model.db.HomeworkDraftRecipientId;
import com.example.footballanalysis.model.db.HomeworkStatus;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.requests.AddHomeworkAssigneesRequest;
import com.example.footballanalysis.model.responses.AssignmentRowResponse;
import com.example.footballanalysis.model.responses.HomeworkRecipientResponse;
import com.example.footballanalysis.repository.HomeworkAssignmentRepository;
import com.example.footballanalysis.repository.HomeworkDraftRecipientRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.TeamRosterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.example.footballanalysis.service.HomeworkGuard.closed;
import static com.example.footballanalysis.service.HomeworkGuard.invalidTransition;
import static com.example.footballanalysis.service.HomeworkGuard.logHomework;

/** Who gets a homework: the draft's chosen players, adding and taking away recipients, and a player leaving the team. */
@Service
@RequiredArgsConstructor
public class HomeworkRecipients {

    private final HomeworkAssignmentRepository assignmentRepository;
    private final HomeworkDraftRecipientRepository draftRecipientRepository;
    private final TeamRosterRepository teamRosterRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final UserAccessService userAccessService;
    private final HomeworkViews views;
    private final HomeworkGuard guard;

    @Transactional(readOnly = true)
    public List<AssignmentRowResponse> listAssignments(UUID teamId, UUID homeworkId, User actor) {
        guard.requireTeam(teamId);
        userAccessService.requireHomeworkRead(actor, teamId);
        return views.assignmentRows(guard.requireHomework(teamId, homeworkId));
    }

    @Transactional
    public List<AssignmentRowResponse> addAssignees(UUID teamId, UUID homeworkId, AddHomeworkAssigneesRequest request, User actor) {
        guard.requireTeam(teamId);
        userAccessService.requireHomeworkWrite(actor, teamId);
        guard.lockTeam(teamId);
        Homework homework = guard.lockHomework(teamId, homeworkId);
        if (homework.getStatus() == HomeworkStatus.DRAFT) {
            throw invalidTransition("A draft has no assignments yet.");
        }
        if (homework.getStatus() != HomeworkStatus.PUBLISHED) {
            throw closed("This homework is closed.");
        }
        List<UUID> requested = request.playerProfileIds() == null ? List.of() : request.playerProfileIds();
        if (requested.isEmpty()) {
            throw new BadRequestException("error.homework.no_assignees", new Object[0], "No players were selected.");
        }
        LocalDateTime now = guard.now();
        // A new row would be overdue at once and could never be handed in.
        if (!homework.isAllowLate() && homework.getDueAt() != null && now.isAfter(homework.getDueAt())) {
            throw new ConflictException(
                    "error.homework.assign_after_deadline",
                    new Object[0],
                    "The deadline has passed and late work is not accepted.");
        }
        List<UUID> onRoster = requireOnRoster(requested, teamId);
        Set<UUID> existing = new HashSet<>(assignmentRepository.findProfileIdsByHomeworkId(homeworkId));
        List<UUID> added = onRoster.stream().filter(profileId -> !existing.contains(profileId)).toList();
        assign(homework, added, now);
        guard.auditHomework(actor, "HOMEWORK_ASSIGNEES_CHANGED", homeworkId, "added=" + added.size() + ",removed=0");
        logHomework("Added homework assignees", teamId, homeworkId);
        return views.assignmentRows(homework);
    }

    @Transactional
    public void removeAssignee(UUID teamId, UUID homeworkId, UUID playerProfileId, User actor) {
        guard.requireTeam(teamId);
        userAccessService.requireHomeworkWrite(actor, teamId);
        Homework homework = guard.lockHomework(teamId, homeworkId);
        HomeworkGuard.requireNotArchived(homework);
        HomeworkAssignment assignment = assignmentRepository.findByHomework_IdAndPlayerProfile_Id(homeworkId, playerProfileId)
                .orElseThrow(() -> new NotFoundException(
                        "error.homework.assignment_not_found",
                        new Object[]{playerProfileId},
                        "Assignment not found."));
        if (assignment.getSubmittedAt() != null) {
            throw invalidTransition("An assignment that was handed in cannot be taken away.");
        }
        assignmentRepository.delete(assignment);
        guard.auditHomework(actor, "HOMEWORK_ASSIGNEES_CHANGED", homeworkId, "added=0,removed=1");
        logHomework("Removed homework assignee", teamId, homeworkId);
    }

    /** The roster for the recipient picker. */
    @Transactional(readOnly = true)
    public List<HomeworkRecipientResponse> listRecipients(UUID teamId, User actor) {
        guard.requireTeam(teamId);
        userAccessService.requireHomeworkRead(actor, teamId);
        return playerProfileRepository.findByTeam_Id(teamId).stream()
                .map(profile -> new HomeworkRecipientResponse(
                        profile.getId(), profile.getFirstName(), profile.getLastName(), profile.getLinkedUser() != null))
                .sorted(Comparator.comparing(HomeworkRecipientResponse::lastName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(HomeworkRecipientResponse::firstName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    /**
     * Runs inside the removal's transaction, under the team lock. All of the player's work on the team goes, handed in or
     * not, so an assignment always belongs to a player on the team.
     */
    @Transactional
    public void releaseForRosterRemoval(UUID profileId, UUID teamId) {
        assignmentRepository.deleteForRosterRemoval(profileId, teamId);
        draftRecipientRepository.deleteAll(draftRecipientRepository.findByPlayerProfile_IdAndHomework_Team_Id(profileId, teamId));
    }

    // The draft's recipients and publishing, for HomeworkService

    public void requireValidDraft(boolean entireRoster, List<UUID> profileIds, UUID teamId) {
        requireCompatible(entireRoster, profileIds);
        if (profileIds != null) {
            requireOnRoster(profileIds, teamId);
        }
    }

    /** Who a publish goes to: the whole roster as it is now, or the chosen players, each still on the team. */
    public List<UUID> forPublish(boolean entireRoster, List<UUID> profileIds, UUID teamId) {
        requireCompatible(entireRoster, profileIds);
        return entireRoster ? teamRosterRepository.findProfileIdsByTeamId(teamId) : requireOnRoster(profileIds, teamId);
    }

    /** @return whether the saved recipients changed */
    public boolean syncDraft(Homework homework, boolean entireRoster, List<UUID> profileIds) {
        boolean changed = homework.isDraftEntireRoster() != entireRoster;
        homework.setDraftEntireRoster(entireRoster);
        if (!entireRoster && profileIds == null) {
            return changed;
        }
        Set<UUID> wanted = entireRoster ? Set.of() : new LinkedHashSet<>(profileIds);
        Set<UUID> saved = new HashSet<>();
        for (HomeworkDraftRecipient recipient : draftRecipientRepository.findByHomework_Id(homework.getId())) {
            UUID profileId = recipient.getId().getPlayerProfileId();
            if (wanted.contains(profileId)) {
                saved.add(profileId);
            } else {
                draftRecipientRepository.delete(recipient);
                changed = true;
            }
        }
        for (UUID profileId : wanted) {
            if (!saved.contains(profileId)) {
                saveDraftRecipient(homework, profileId);
                changed = true;
            }
        }
        return changed;
    }

    public void clearDraft(UUID homeworkId) {
        draftRecipientRepository.deleteAll(draftRecipientRepository.findByHomework_Id(homeworkId));
    }

    public void saveDraftRecipient(Homework homework, UUID profileId) {
        HomeworkDraftRecipient recipient = new HomeworkDraftRecipient();
        recipient.setId(new HomeworkDraftRecipientId(homework.getId(), profileId));
        recipient.setHomework(homework);
        recipient.setPlayerProfile(playerProfileRepository.getReferenceById(profileId));
        draftRecipientRepository.save(recipient);
    }

    public void assign(Homework homework, List<UUID> profileIds, LocalDateTime now) {
        for (UUID profileId : profileIds) {
            HomeworkAssignment assignment = new HomeworkAssignment();
            assignment.setHomework(homework);
            assignment.setPlayerProfile(playerProfileRepository.getReferenceById(profileId));
            assignment.setStatus(HomeworkAssignmentStatus.ASSIGNED);
            assignment.setAssignedAt(now);
            assignment.setUpdatedAt(now);
            assignmentRepository.save(assignment);
        }
    }

    private static void requireCompatible(boolean entireRoster, List<UUID> profileIds) {
        if (entireRoster && profileIds != null && !profileIds.isEmpty()) {
            throw new BadRequestException(
                    "error.homework.invalid_recipients",
                    new Object[0],
                    "Choose either the entire roster or a list of players.");
        }
    }

    /** @return the ids without duplicates */
    private List<UUID> requireOnRoster(List<UUID> profileIds, UUID teamId) {
        if (profileIds == null || profileIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> distinct = new LinkedHashSet<>(profileIds);
        Set<UUID> onRoster = new HashSet<>(teamRosterRepository.findProfileIdsOnTeam(teamId, distinct));
        for (UUID profileId : distinct) {
            if (!onRoster.contains(profileId)) {
                throw new NotFoundException(
                        "error.player_profile.not_found",
                        new Object[]{profileId},
                        "Player profile not found on this team.");
            }
        }
        return List.copyOf(distinct);
    }
}
