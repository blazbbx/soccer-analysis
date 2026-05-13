package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.TeamMessage;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.requests.ChatMessageRequest;
import com.example.footballanalysis.model.responses.ChatHistoryResponse;
import com.example.footballanalysis.model.responses.ChatMessageResponse;
import com.example.footballanalysis.repository.TeamMessageRepository;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final TeamMessageRepository teamMessageRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final UserAccessService userAccessService;
    private final SseNotificationService sseNotificationService;

    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 50;
    private static final int MAX_MESSAGE_PAGE_SIZE = 50;

    /**
     * Üzenet küldése egy csapatnak.
     * 
     * @param teamId a csapat azonosítója
     * @param sender az üzenet küldője
     * @param request az üzenet tartalma
     * @return az elmentett üzenet DTO-ként
     * @throws UnauthorizedException ha a felhasználónak nincs jogosultsága az adott csapathoz
     * @throws NotFoundException ha a csapat vagy a felhasználó nem létezik
     */
    @Transactional
    public ChatMessageResponse sendMessage(UUID teamId, User sender, ChatMessageRequest request) {
        // Jogosultság ellenőrzése
        if (!userAccessService.hasTeamAccess(sender, Collections.singletonList(teamId))) {
            log.atWarn()
                    .setMessage("User {} attempted to send message to team {} without access")
                    .addArgument(sender.getId())
                    .addArgument(teamId)
                    .addKeyValue("event_type", "TEAM_CHAT_UNAUTHORIZED")
                    .addKeyValue("team_id", teamId)
                    .addKeyValue("user_id", sender.getId())
                    .log();
            throw new UnauthorizedException("error.auth.forbidden", new Object[0], "Access denied to team");
        }

        // Csapat meglétének ellenőrzése
        if (!teamRepository.existsById(teamId)) {
            throw new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found: " + teamId);
        }

        // Üzenet létrehozása és mentése
        TeamMessage teamMessage = new TeamMessage();
        teamMessage.setTeamId(teamId);
        teamMessage.setSender(sender);
        teamMessage.setContent(request.content());

        TeamMessage savedMessage = teamMessageRepository.save(teamMessage);

        // Strukturált logolás
        String traceId = MDC.get("traceId");
        log.atInfo()
                .setMessage("Team message saved: teamId={}, senderId={}, messageId={}")
                .addArgument(teamId)
                .addArgument(sender.getId())
                .addArgument(savedMessage.getId())
                .addKeyValue("event_type", "TEAM_CHAT_MESSAGE")
                .addKeyValue("team_id", teamId)
                .addKeyValue("sender_id", sender.getId())
                .addKeyValue("message_id", savedMessage.getId())
                .addKeyValue("trace_id", traceId)
                .log();

        // Valós idejű értesítés küldése az online csapattagoknak
        ChatMessageResponse response = mapToResponse(savedMessage);
        sseNotificationService.broadcastToTeam(teamId, "TEAM_CHAT_MESSAGE", response);

        return response;
    }

    /**
     * Egy csapat üzenetélőzményeinek lapozott lekérése.
     * Ha nincs kurzor megadva, az utolsó 50 üzenetet adja vissza.
     * 
     * @param teamId a csapat azonosítója
     * @param requester az igénylő felhasználó (jogosultság ellenőrzéshez)
     * @param beforeCreatedAt kurzor: ennél régebbi üzenetek kellenek
     * @param limit oldal méret (max 50)
     * @return az üzenetek listája növekvő időrendben, lapozási metaadatokkal
     * @throws UnauthorizedException ha az igénylőnek nincs jogosultsága az adott csapathoz
     * @throws NotFoundException ha a csapat nem létezik
     */
    @Transactional(readOnly = true)
    public ChatHistoryResponse getHistory(UUID teamId, User requester, LocalDateTime beforeCreatedAt, Integer limit) {
        // Jogosultság ellenőrzése
        if (!userAccessService.hasTeamAccess(requester, Collections.singletonList(teamId))) {
            log.atWarn()
                    .setMessage("User {} attempted to fetch history for team {} without access")
                    .addArgument(requester.getId())
                    .addArgument(teamId)
                    .addKeyValue("event_type", "TEAM_CHAT_HISTORY_UNAUTHORIZED")
                    .addKeyValue("team_id", teamId)
                    .addKeyValue("user_id", requester.getId())
                    .log();
            throw new UnauthorizedException("error.auth.forbidden", new Object[0], "Access denied to team");
        }

        // Csapat meglétének ellenőrzése
        if (!teamRepository.existsById(teamId)) {
            throw new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found: " + teamId);
        }

        int pageSize = resolvePageSize(limit);

        // +1 elemet kérünk, hogy ki tudjuk számolni a hasMore értéket.
        PageRequest pageRequest = PageRequest.of(0, pageSize + 1);
        List<TeamMessage> messages = new ArrayList<>(beforeCreatedAt == null
                ? teamMessageRepository.findLatestMessagesByTeamId(teamId, pageRequest)
                : teamMessageRepository.findMessagesBefore(teamId, beforeCreatedAt, pageRequest));

        boolean hasMore = messages.size() > pageSize;
        if (hasMore) {
            messages = messages.subList(0, pageSize);
        }

        // Fordított sorrend (hogy időrendben növekvő legyen az eredmény)
        Collections.reverse(messages);

        LocalDateTime nextBeforeCreatedAt = hasMore && !messages.isEmpty()
                ? messages.get(0).getCreatedAt()
                : null;

        log.atDebug()
                .setMessage("Fetched {} messages for teamId={}")
                .addArgument(messages.size())
                .addArgument(teamId)
                .addKeyValue("event_type", "TEAM_CHAT_HISTORY_FETCHED")
                .addKeyValue("team_id", teamId)
                .addKeyValue("user_id", requester.getId())
                .addKeyValue("message_count", messages.size())
                .log();

        List<ChatMessageResponse> messageResponses = messages.stream()
                .map(this::mapToResponse)
                .toList();

        return new ChatHistoryResponse(messageResponses, hasMore, nextBeforeCreatedAt);
    }

    private int resolvePageSize(Integer limit) {
        if (limit == null) {
            return DEFAULT_MESSAGE_PAGE_SIZE;
        }
        if (limit < 1 || limit > MAX_MESSAGE_PAGE_SIZE) {
            throw new BadRequestException(
                    "error.validation.invalid_request",
                    new Object[]{limit},
                    "Invalid chat history limit. Expected value between 1 and 50"
            );
        }
        return limit;
    }

    /**
     * A TeamMessage entitást ChatMessageResponse DTO-vá alakítja.
     */
    private ChatMessageResponse mapToResponse(TeamMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getTeamId(),
                message.getSender().getId(),
                message.getSender().getFullName(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
