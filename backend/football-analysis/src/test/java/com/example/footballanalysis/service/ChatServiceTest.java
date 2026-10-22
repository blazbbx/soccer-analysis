package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.model.db.TeamMessage;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.responses.ChatHistoryResponse;
import com.example.footballanalysis.model.responses.ChatChildName;
import com.example.footballanalysis.model.responses.ParentChildName;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    private ChatService createService() {
        return new ChatService(
                teamMessageRepository,
                teamRepository,
                userRepository,
                userAccessService,
                sseNotificationService,
                playerParentRepository
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
        ChatHistoryResponse response = chatService.getHistory(teamId, requester, null, null, null);

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
        ChatHistoryResponse response = chatService.getHistory(teamId, requester, beforeCreatedAt, null, 50);

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

        ChatHistoryResponse response = createService().getHistory(teamId, requester, at, cursorId, 50);

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

        ChatHistoryResponse response = createService().getHistory(teamId, parent, null, null, null);

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

        assertThatThrownBy(() -> chatService.getHistory(teamId, requester, null, null, 51))
                .isInstanceOf(BadRequestException.class);

        verify(teamMessageRepository, never()).findLatestMessagesByTeamId(any(), any(Pageable.class));
        verify(teamMessageRepository, never()).findMessagesBefore(any(), any(), any(Pageable.class));
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

