package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.Admin;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SseNotificationService team chat broadcast")
class SseNotificationServiceTest {

    @Mock private MatchRepository matchRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserAccessService userAccessService;

    @InjectMocks
    private SseNotificationService sseNotificationService;

    @Test
    void broadcastToTeam_deliversOnlyToTheOpenTeamChat() {
        UUID teamId = UUID.randomUUID();
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        admin.setRole(UserRole.ADMIN);

        sseNotificationService.subscribeToTeamChat(admin, teamId);
        sseNotificationService.broadcastToTeam(teamId, "TEAM_CHAT_MESSAGE", "hello");
        sseNotificationService.broadcastToTeam(UUID.randomUUID(), "TEAM_CHAT_MESSAGE", "other-team");

        verify(userAccessService).requireTeamChatAccess(admin, teamId);
        verify(userAccessService, never()).hasTeamAccess(any(), any());
        verify(userRepository, never()).findById(any());
    }
}
