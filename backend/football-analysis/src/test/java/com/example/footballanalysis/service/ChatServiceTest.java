package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.TeamMessage;
import com.example.footballanalysis.model.db.TeamMessageType;
import com.example.footballanalysis.model.requests.ChatMessageRequest;
import com.example.footballanalysis.model.responses.ChatMessageResponse;
import com.example.footballanalysis.model.responses.ChatSharedClipResponse;
import com.example.footballanalysis.model.db.user.Admin;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.responses.ChatHistoryResponse;
import com.example.footballanalysis.model.responses.ChatChildName;
import com.example.footballanalysis.model.responses.ParentChildName;
import com.example.footballanalysis.repository.ClipRepository;
import com.example.footballanalysis.repository.PlayerParentRepository;
import com.example.footballanalysis.repository.TeamMessageRepository;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatService pagination tests")
class ChatServiceTest {

    @Mock
    private TeamMessageRepository teamMessageRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAccessService userAccessService;

    @Mock
    private SseNotificationService sseNotificationService;

    @Mock
    private PlayerParentRepository playerParentRepository;

    @Mock
    private ClipRepository clipRepository;

    private ChatService createService() {
        return new ChatService(
                teamMessageRepository,
                teamRepository,
                userRepository,
                userAccessService,
                sseNotificationService,
                playerParentRepository,
                clipRepository
        );
    }

    @Test
    void getHistory_returnsInitial50MessagesAndPaginationCursor() {
        UUID teamId = UUID.randomUUID();
        User requester = coach(UUID.randomUUID());
        LocalDateTime base = LocalDateTime.of(2026, 5, 10, 12, 0);

        List<TeamMessage> latestMessages = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            latestMessages.add(message(teamId, requester, base.minusMinutes(i)));
        }

        when(userAccessService.canAccessTeamChat(requester, teamId)).thenReturn(true);
        when(teamRepository.existsById(teamId)).thenReturn(true);
        when(teamMessageRepository.findLatestMessagesByTeamId(ArgumentMatchers.eq(teamId), any(Pageable.class)))
                .thenReturn(latestMessages);

        ChatService chatService = createService();
        ChatHistoryResponse response = chatService.getHistory(teamId, requester, null, null, null, null, null);

        assertThat(response.messages()).hasSize(50);
        assertThat(response.hasMore()).isTrue();
        assertThat(response.messages().get(0).createdAt()).isEqualTo(base.minusMinutes(49));
        assertThat(response.messages().get(49).createdAt()).isEqualTo(base);
        assertThat(response.nextBeforeCreatedAt()).isEqualTo(base.minusMinutes(49));
        assertThat(response.nextBeforeId()).isEqualTo(latestMessages.get(49).getId());

        verify(teamMessageRepository, never()).findMessagesBefore(any(), any(), any(Pageable.class));
        verify(teamMessageRepository, never()).findMessagesBefore(any(), any(), any(UUID.class), any(Pageable.class));
    }

    @Test
    void getHistory_withCursorLoadsOlderMessages() {
        UUID teamId = UUID.randomUUID();
        User requester = coach(UUID.randomUUID());
        LocalDateTime beforeCreatedAt = LocalDateTime.of(2026, 5, 10, 11, 0);

        TeamMessage older1 = message(teamId, requester, beforeCreatedAt.minusMinutes(1));
        TeamMessage older2 = message(teamId, requester, beforeCreatedAt.minusMinutes(2));

        when(userAccessService.canAccessTeamChat(requester, teamId)).thenReturn(true);
        when(teamRepository.existsById(teamId)).thenReturn(true);
        when(teamMessageRepository.findMessagesBefore(ArgumentMatchers.eq(teamId), ArgumentMatchers.eq(beforeCreatedAt), any(Pageable.class)))
                .thenReturn(List.of(older1, older2));

        ChatService chatService = createService();
        ChatHistoryResponse response = chatService.getHistory(teamId, requester, beforeCreatedAt, null, null, null, 50);

        assertThat(response.messages()).hasSize(2);
        assertThat(response.messages().get(0).createdAt()).isEqualTo(beforeCreatedAt.minusMinutes(2));
        assertThat(response.messages().get(1).createdAt()).isEqualTo(beforeCreatedAt.minusMinutes(1));
        assertThat(response.hasMore()).isFalse();
        assertThat(response.nextBeforeCreatedAt()).isNull();
        assertThat(response.nextBeforeId()).isNull();

        verify(teamMessageRepository, never()).findLatestMessagesByTeamId(any(), any(Pageable.class));
        verify(teamMessageRepository, never()).findMessagesBefore(any(), any(), any(UUID.class), any(Pageable.class));
    }

    @Test
    void getHistory_sameTimestampUsesMessageIdCursor() {
        UUID teamId = UUID.randomUUID();
        User requester = coach(UUID.randomUUID());
        LocalDateTime at = LocalDateTime.of(2026, 5, 10, 11, 0);
        UUID cursorId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        TeamMessage sameInstant = message(teamId, requester, at);
        sameInstant.setId(UUID.fromString("00000000-0000-0000-0000-000000000009"));
        TeamMessage older = message(teamId, requester, at.minusMinutes(1));

        when(userAccessService.canAccessTeamChat(requester, teamId)).thenReturn(true);
        when(teamRepository.existsById(teamId)).thenReturn(true);
        when(teamMessageRepository.findMessagesBefore(
                ArgumentMatchers.eq(teamId), ArgumentMatchers.eq(at), ArgumentMatchers.eq(cursorId), any(Pageable.class)))
                .thenReturn(List.of(sameInstant, older));

        ChatHistoryResponse response = createService().getHistory(teamId, requester, at, cursorId, null, null, 50);

        assertThat(response.messages()).extracting(message -> message.id())
                .containsExactly(older.getId(), sameInstant.getId());
        assertThat(response.hasMore()).isFalse();
        assertThat(response.nextBeforeId()).isNull();
        verify(teamMessageRepository, never()).findMessagesBefore(any(), any(), any(Pageable.class));
    }

    @Test
    void getHistory_namesChildrenOfParentOnThatTeam() {
        UUID teamId = UUID.randomUUID();
        User parent = coach(UUID.randomUUID());
        TeamMessage message = message(teamId, parent, LocalDateTime.of(2026, 5, 10, 12, 0));

        when(userAccessService.canAccessTeamChat(parent, teamId)).thenReturn(true);
        when(teamRepository.existsById(teamId)).thenReturn(true);
        when(teamMessageRepository.findLatestMessagesByTeamId(ArgumentMatchers.eq(teamId), any(Pageable.class)))
                .thenReturn(List.of(message));
        when(playerParentRepository.findChildrenOnTeam(List.of(parent.getId()), teamId))
                .thenReturn(List.of(new ParentChildName(parent.getId(), "Peti", "Kiss")));

        ChatHistoryResponse response = createService().getHistory(teamId, parent, null, null, null, null, null);

        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().get(0).senderChildNames()).containsExactly(new ChatChildName("Peti", "Kiss"));
    }

    @Test
    void getHistory_rejectsInvalidLimit() {
        UUID teamId = UUID.randomUUID();
        User requester = coach(UUID.randomUUID());

        when(userAccessService.canAccessTeamChat(requester, teamId)).thenReturn(true);
        when(teamRepository.existsById(teamId)).thenReturn(true);

        ChatService chatService = createService();

        assertThatThrownBy(() -> chatService.getHistory(teamId, requester, null, null, null, null, 51))
                .isInstanceOf(BadRequestException.class);

        verify(teamMessageRepository, never()).findLatestMessagesByTeamId(any(), any(Pageable.class));
        verify(teamMessageRepository, never()).findMessagesBefore(any(), any(), any(Pageable.class));
    }

    @Test
    void getHistory_afterCursorReturnsNewerMessagesInAscendingOrder() {
        UUID teamId = UUID.randomUUID();
        User requester = coach(UUID.randomUUID());
        LocalDateTime at = LocalDateTime.of(2026, 5, 10, 11, 0);
        UUID afterId = UUID.fromString("00000000-0000-0000-0000-000000000010");

        // The query is ordered oldest first; same createdAt with a larger id comes right after the cursor.
        TeamMessage sameInstant = message(teamId, requester, at);
        sameInstant.setId(UUID.fromString("00000000-0000-0000-0000-000000000011"));
        List<TeamMessage> newer = new ArrayList<>(List.of(sameInstant));
        for (int i = 1; i <= 50; i++) {
            newer.add(message(teamId, requester, at.plusMinutes(i)));
        }

        when(userAccessService.canAccessTeamChat(requester, teamId)).thenReturn(true);
        when(teamRepository.existsById(teamId)).thenReturn(true);
        when(teamMessageRepository.findMessagesAfter(
                ArgumentMatchers.eq(teamId), ArgumentMatchers.eq(at), ArgumentMatchers.eq(afterId), any(Pageable.class)))
                .thenReturn(newer);

        ChatHistoryResponse response = createService().getHistory(teamId, requester, null, null, at, afterId, 50);

        assertThat(response.messages()).hasSize(50);
        assertThat(response.messages().get(0).id()).isEqualTo(sameInstant.getId());
        assertThat(response.messages().get(49).createdAt()).isEqualTo(at.plusMinutes(49));
        assertThat(response.hasMore()).isTrue();
        assertThat(response.nextBeforeCreatedAt()).isNull();
        assertThat(response.nextBeforeId()).isNull();
        verify(teamMessageRepository, never()).findLatestMessagesByTeamId(any(), any(Pageable.class));
    }

    @Test
    void getHistory_afterCursorWithoutIdIsRejected() {
        UUID teamId = UUID.randomUUID();
        User requester = coach(UUID.randomUUID());

        when(userAccessService.canAccessTeamChat(requester, teamId)).thenReturn(true);
        when(teamRepository.existsById(teamId)).thenReturn(true);

        assertThatThrownBy(() -> createService().getHistory(
                teamId, requester, null, null, LocalDateTime.of(2026, 5, 10, 11, 0), null, 50))
                .isInstanceOf(BadRequestException.class);
        verify(teamMessageRepository, never()).findMessagesAfter(any(), any(), any(), any(Pageable.class));
    }

    @Test
    void sendMessage_withoutClipAndBlankContentIsRejected() {
        UUID teamId = UUID.randomUUID();
        User sender = coach(UUID.randomUUID());
        when(userAccessService.canAccessTeamChat(sender, teamId)).thenReturn(true);
        when(teamRepository.existsById(teamId)).thenReturn(true);

        assertThatThrownBy(() -> createService().sendMessage(teamId, sender, new ChatMessageRequest("   ", null)))
                .isInstanceOf(BadRequestException.class)
                .extracting("messageCode").isEqualTo("validation.message.content.required");
        verify(teamMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_clipOfAnotherTeamIs403() {
        UUID teamId = UUID.randomUUID();
        User sender = coach(UUID.randomUUID());
        Clip clip = clip("COMPLETED");
        allowSend(sender, teamId, clip);
        when(userAccessService.isClipOfTeam(clip.getId(), teamId)).thenReturn(false);

        assertThatThrownBy(() -> createService().sendMessage(teamId, sender, new ChatMessageRequest("", clip.getId())))
                .isInstanceOf(ForbiddenException.class)
                .extracting("messageCode").isEqualTo("error.chat.clip_not_in_team");
        verify(teamMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_clipNotRenderedIs400() {
        UUID teamId = UUID.randomUUID();
        User sender = coach(UUID.randomUUID());
        for (String status : List.of("QUEUED", "PROCESSING", "PENDING_UPLOAD", "FAILED")) {
            Clip clip = clip(status);
            allowSend(sender, teamId, clip);
            when(userAccessService.isClipOfTeam(clip.getId(), teamId)).thenReturn(true);

            assertThatThrownBy(() -> createService().sendMessage(teamId, sender, new ChatMessageRequest("", clip.getId())))
                    .isInstanceOf(BadRequestException.class)
                    .extracting("messageCode").isEqualTo("error.chat.clip_not_ready");
        }
        verify(teamMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_clipWithoutClipAccessIs403() {
        UUID teamId = UUID.randomUUID();
        User sender = coach(UUID.randomUUID());
        Clip clip = clip("COMPLETED");
        allowSend(sender, teamId, clip);
        doThrow(new ForbiddenException("error.auth.forbidden", new Object[0], "no clip"))
                .when(userAccessService).requireClipAccess(sender, clip.getId());

        assertThatThrownBy(() -> createService().sendMessage(teamId, sender, new ChatMessageRequest("", clip.getId())))
                .isInstanceOf(ForbiddenException.class);
        verify(teamMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_completedClipOfTheTeamIsSavedAsClipMessage() {
        UUID teamId = UUID.randomUUID();
        User sender = coach(UUID.randomUUID());
        Clip clip = clip("COMPLETED");
        allowSend(sender, teamId, clip);
        when(userAccessService.isClipOfTeam(clip.getId(), teamId)).thenReturn(true);
        when(teamMessageRepository.save(any(TeamMessage.class))).thenAnswer(invocation -> {
            TeamMessage saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(LocalDateTime.of(2026, 5, 10, 12, 0));
            return saved;
        });
        when(clipRepository.findAllWithMatchByIdIn(anyCollection())).thenReturn(List.of(clip));

        ChatMessageResponse response = createService().sendMessage(teamId, sender, new ChatMessageRequest(null, clip.getId()));

        assertThat(response.type()).isEqualTo("CLIP");
        assertThat(response.content()).isEmpty();
        assertThat(response.clip().id()).isEqualTo(clip.getId());
        assertThat(response.clip().name()).isEqualTo("Goal");
        assertThat(response.clip().matchDisplayName()).isEqualTo("Home FC vs Away FC");
    }

    @Test
    void getHistory_clipMessageOfDeletedClipHasNoClip() {
        UUID teamId = UUID.randomUUID();
        User requester = coach(UUID.randomUUID());
        TeamMessage deletedClipMessage = message(teamId, requester, LocalDateTime.of(2026, 5, 10, 12, 0));
        deletedClipMessage.setType(TeamMessageType.CLIP);

        when(userAccessService.canAccessTeamChat(requester, teamId)).thenReturn(true);
        when(teamRepository.existsById(teamId)).thenReturn(true);
        when(teamMessageRepository.findLatestMessagesByTeamId(ArgumentMatchers.eq(teamId), any(Pageable.class)))
                .thenReturn(List.of(deletedClipMessage));

        ChatHistoryResponse response = createService().getHistory(teamId, requester, null, null, null, null, null);

        assertThat(response.messages().get(0).type()).isEqualTo("CLIP");
        assertThat(response.messages().get(0).clip()).isNull();
        verify(clipRepository, never()).findAllWithMatchByIdIn(anyCollection());
    }

    @Test
    void getSharedClips_listsEachClipOnceWithItsLatestShare() {
        UUID teamId = UUID.randomUUID();
        User requester = coach(UUID.randomUUID());
        Clip goal = clip("COMPLETED");
        Clip save = clip("COMPLETED");
        TeamMessage latestGoal = clipMessage(teamId, requester, goal, LocalDateTime.of(2026, 5, 10, 12, 0));
        TeamMessage latestSave = clipMessage(teamId, requester, save, LocalDateTime.of(2026, 5, 9, 12, 0));
        TeamMessage olderGoal = clipMessage(teamId, requester, goal, LocalDateTime.of(2026, 5, 8, 12, 0));

        when(teamMessageRepository.findLatestClipMessages(ArgumentMatchers.eq(teamId), any(Pageable.class)))
                .thenReturn(List.of(latestGoal, latestSave, olderGoal));
        when(clipRepository.findAllWithMatchByIdIn(anyCollection())).thenReturn(List.of(goal, save));

        List<ChatSharedClipResponse> shared = createService().getSharedClips(teamId, requester);

        verify(userAccessService).requireTeamChatAccess(requester, teamId);
        assertThat(shared).extracting(ChatSharedClipResponse::messageId)
                .containsExactly(latestGoal.getId(), latestSave.getId());
    }

    @Test
    void getHistory_roleLabelComesFromThisTeamNotFromThePrimaryRole() {
        UUID teamId = UUID.randomUUID();
        User requester = coach(UUID.randomUUID());
        // Primary role CLUB_ADMIN, but of another club; writes here as a parent: no label.
        Parent otherClubAdmin = parentUser(UserRole.CLUB_ADMIN);
        // Primary role PARENT, but admin of this team's club: CLUB_ADMIN.
        Parent thisClubAdmin = parentUser(UserRole.PARENT);
        // Primary role PARENT, coach of this team: COACH.
        Parent coachOfTeam = parentUser(UserRole.PARENT);
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        admin.setRole(UserRole.ADMIN);
        LocalDateTime at = LocalDateTime.of(2026, 5, 10, 12, 0);
        List<TeamMessage> page = List.of(
                message(teamId, admin, at.plusMinutes(4)),
                message(teamId, coachOfTeam, at.plusMinutes(3)),
                message(teamId, thisClubAdmin, at.plusMinutes(2)),
                message(teamId, otherClubAdmin, at.plusMinutes(1)));

        when(userAccessService.canAccessTeamChat(requester, teamId)).thenReturn(true);
        when(teamRepository.existsById(teamId)).thenReturn(true);
        when(teamMessageRepository.findLatestMessagesByTeamId(ArgumentMatchers.eq(teamId), any(Pageable.class)))
                .thenReturn(page);
        when(teamRepository.findClubAdminIdsOfTeam(teamId,
                java.util.Set.of(otherClubAdmin.getId(), thisClubAdmin.getId(), coachOfTeam.getId())))
                .thenReturn(List.of(thisClubAdmin.getId()));
        when(teamRepository.findCoachIdsOfTeam(teamId, java.util.Set.of(otherClubAdmin.getId(), coachOfTeam.getId())))
                .thenReturn(List.of(coachOfTeam.getId()));

        ChatHistoryResponse response = createService().getHistory(teamId, requester, null, null, null, null, null);

        assertThat(response.messages()).extracting(ChatMessageResponse::senderRole)
                .containsExactly(null, "CLUB_ADMIN", "COACH", "ADMIN");
        verify(teamRepository).findClubAdminIdsOfTeam(any(), any());
        verify(teamRepository).findCoachIdsOfTeam(any(), any());
    }

    private static Parent parentUser(UserRole primaryRole) {
        Parent parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setRole(primaryRole);
        return parent;
    }

    private void savesMessages() {
        when(teamMessageRepository.save(any(TeamMessage.class))).thenAnswer(invocation -> {
            TeamMessage saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(LocalDateTime.of(2026, 5, 10, 12, 0));
            return saved;
        });
    }

    private void allowSend(User sender, UUID teamId, Clip clip) {
        when(userAccessService.canAccessTeamChat(sender, teamId)).thenReturn(true);
        when(teamRepository.existsById(teamId)).thenReturn(true);
        when(clipRepository.findById(clip.getId())).thenReturn(Optional.of(clip));
    }

    private static Clip clip(String renderStatus) {
        Team home = new Team();
        home.setId(UUID.randomUUID());
        home.setName("Home FC");
        Team away = new Team();
        away.setId(UUID.randomUUID());
        away.setName("Away FC");
        Match match = new Match();
        match.setId(UUID.randomUUID());
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        Clip clip = new Clip();
        clip.setId(UUID.randomUUID());
        clip.setName("Goal");
        clip.setMatch(match);
        clip.setRenderStatus(renderStatus);
        return clip;
    }

    private TeamMessage clipMessage(UUID teamId, User sender, Clip clip, LocalDateTime createdAt) {
        TeamMessage message = message(teamId, sender, createdAt);
        message.setType(TeamMessageType.CLIP);
        message.setClip(clip);
        return message;
    }

    private Coach coach(UUID id) {
        Coach coach = new Coach();
        coach.setId(id);
        coach.setFirstName("Test");
        coach.setLastName("Coach");
        return coach;
    }

    private TeamMessage message(UUID teamId, User sender, LocalDateTime createdAt) {
        TeamMessage message = new TeamMessage();
        message.setId(UUID.randomUUID());
        message.setTeamId(teamId);
        message.setSender(sender);
        message.setContent("hello");
        message.setCreatedAt(createdAt);
        return message;
    }
}

