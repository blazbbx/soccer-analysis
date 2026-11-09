package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.model.db.HomeworkAssignment;
import com.example.footballanalysis.model.db.PlayerParentId;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.repository.PlayerParentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Who may answer an assignment: the linked player or a parent of the profile, even when the child has a login. An
 * assignment exists only for a published homework and a player on the team (leaving the team deletes it), so this is the
 * whole check.
 * Hat and club checks stay on {@link UserAccessService}.
 */
@Service
@RequiredArgsConstructor
public class HomeworkAccess {

    private final UserAccessService userAccessService;
    private final PlayerParentRepository playerParentRepository;

    public void requireAssignee(User actor, HomeworkAssignment assignment) {
        if (!isAssignee(actor, assignment)) {
            throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied to assignment.");
        }
    }

    public boolean isAssignee(User actor, HomeworkAssignment assignment) {
        if (actor == null || actor.getId() == null || assignment == null || assignment.getPlayerProfile() == null) {
            return false;
        }
        return canActFor(actor, assignment.getPlayerProfile());
    }

    public boolean canActFor(User actor, PlayerProfile profile) {
        return actor != null && actor.getId() != null && (isLinkedPlayer(actor, profile) || isParent(actor, profile));
    }

    /** The answer the audit and the {@code submittedBy} label need: a linked profile is a player, a parent row a parent. */
    public boolean isLinkedPlayer(User actor, PlayerProfile profile) {
        return actor != null
                && actor.getId() != null
                && profile.getLinkedUser() != null
                && actor.getId().equals(profile.getLinkedUser().getId())
                && userAccessService.hasHat(actor, UserRole.PLAYER);
    }

    private boolean isParent(User actor, PlayerProfile profile) {
        return userAccessService.hasHat(actor, UserRole.PARENT)
                && playerParentRepository.existsById(new PlayerParentId(actor.getId(), profile.getId()));
    }
}
