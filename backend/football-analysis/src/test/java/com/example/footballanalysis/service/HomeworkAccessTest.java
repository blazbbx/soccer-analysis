package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.model.db.HomeworkAssignment;
import com.example.footballanalysis.model.db.PlayerParentId;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.repository.PlayerParentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class HomeworkAccessTest {

    @Mock private UserAccessService userAccessService;
    @Mock private PlayerParentRepository playerParentRepository;
    @InjectMocks private HomeworkAccess access;

    private Player linked;
    private HomeworkAssignment assignment;

    @BeforeEach
    void fixtures() {
        lenient().when(userAccessService.hasHat(any(), any())).thenAnswer(invocation -> {
            User actor = invocation.getArgument(0);
            UserRole role = invocation.getArgument(1);
            return actor != null && actor.getRole() == role;
        });
        linked = player();
        PlayerProfile profile = new PlayerProfile();
        profile.setId(UUID.randomUUID());
        profile.setLinkedUser(linked);
        assignment = new HomeworkAssignment();
        assignment.setPlayerProfile(profile);
    }

    @Test
    void theLinkedPlayerAnswersTheirOwnButNotAnothers() {
        assertThatCode(() -> access.requireAssignee(linked, assignment)).doesNotThrowAnyException();
        assertThat(access.isLinkedPlayer(linked, assignment.getPlayerProfile())).isTrue();

        assertThatThrownBy(() -> access.requireAssignee(player(), assignment)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aParentAnswersForTheirChildEvenWhenTheChildHasALogin() {
        Parent parent = parent();
        lenient().when(playerParentRepository.existsById(new PlayerParentId(parent.getId(), assignment.getPlayerProfile().getId())))
                .thenReturn(true);

        assertThatCode(() -> access.requireAssignee(parent, assignment)).doesNotThrowAnyException();
        assertThat(access.isLinkedPlayer(parent, assignment.getPlayerProfile())).isFalse();
    }

    @Test
    void aParentOfAnotherChildGetsNothing() {
        assertThatThrownBy(() -> access.requireAssignee(parent(), assignment)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void theLinkAloneIsNotEnoughWithoutThePlayerHat() {
        Parent notAPlayer = parent();
        assignment.getPlayerProfile().setLinkedUser(notAPlayer);

        assertThat(access.isAssignee(notAPlayer, assignment)).isFalse();
        assertThat(access.isAssignee(null, assignment)).isFalse();
    }

    private static Player player() {
        Player player = new Player();
        player.setId(UUID.randomUUID());
        player.setRole(UserRole.PLAYER);
        return player;
    }

    private static Parent parent() {
        Parent parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setRole(UserRole.PARENT);
        return parent;
    }
}
