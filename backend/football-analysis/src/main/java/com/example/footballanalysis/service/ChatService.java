package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.TeamMessage;
import com.example.footballanalysis.model.db.TeamMessageType;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.ChatMessageRequest;
import com.example.footballanalysis.model.responses.ChatHistoryResponse;
import com.example.footballanalysis.model.responses.ChatMessageResponse;
import com.example.footballanalysis.model.responses.ChatChildName;
import com.example.footballanalysis.model.responses.ChatClipSummary;
import com.example.footballanalysis.model.responses.ChatSharedClipResponse;
import com.example.footballanalysis.model.responses.ParentChildName;
import com.example.footballanalysis.repository.ClipRepository;
import com.example.footballanalysis.repository.PlayerParentRepository;
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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final TeamMessageRepository teamMessageRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final UserAccessService userAccessService;
    private final SseNotificationService sseNotificationService;
    private final PlayerParentRepository playerParentRepository;
    private final ClipRepository clipRepository;
    private final PlatformAdminDirectory platformAdminDirectory;

    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 50;
    private static final int MAX_MESSAGE_PAGE_SIZE = 50;
    /** The shared clips section looks at this many latest clip messages ... */
    private static final int SHARED_CLIP_MESSAGES_SCANNED = 50;
    /** ... and lists at most this many different clips. */
    private static final int SHARED_CLIPS_LISTED = 20;

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
        if (!userAccessService.canAccessTeamChat(sender, teamId)) {
            log.atWarn()
                    .setMessage("User {} attempted to send message to team {} without access")
                    .addArgument(sender.getId())
                    .addArgument(teamId)
                    .addKeyValue("event_type", "TEAM_CHAT_UNAUTHORIZED")
                    .addKeyValue("team_id", teamId)
                    .addKeyValue("user_id", sender.getId())
                    .log();
            throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied to team");
        }

        // Csapat meglétének ellenőrzése
        if (!teamRepository.existsById(teamId)) {
            throw new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found: " + teamId);
        }

        // Klipmegosztás: aki látja a klipet, a klip a csapat meccséé, és elkészült
        Clip clip = request.clipId() == null ? null : shareableClip(sender, teamId, request.clipId());
        String content = request.content() == null ? "" : request.content();
        if (clip == null && content.isBlank()) {
            throw new BadRequestException("validation.message.content.required", new Object[0], "Message content is required");
        }

        // Üzenet létrehozása és mentése
        TeamMessage teamMessage = new TeamMessage();
        teamMessage.setTeamId(teamId);
        teamMessage.setSender(sender);
        teamMessage.setContent(content);
        teamMessage.setType(clip == null ? TeamMessageType.TEXT : TeamMessageType.CLIP);
        teamMessage.setClip(clip);

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
        ChatMessageResponse response = mapMessages(List.of(savedMessage)).get(0);
        sseNotificationService.broadcastToTeamAfterCommit(teamId, "TEAM_CHAT_MESSAGE", response);

        return response;
    }

    /**
     * Egy csapat üzenetélőzményeinek lapozott lekérése.
     * Ha nincs kurzor megadva, az utolsó 50 üzenetet adja vissza.
     * 
     * @param teamId a csapat azonosítója
     * @param requester az igénylő felhasználó (jogosultság ellenőrzéshez)
     * @param beforeCreatedAt kurzor: ennél régebbi üzenetek kellenek
     * @param afterCreatedAt kurzor: ennél újabb üzenetek kellenek (az afterId-vel együtt);
     *                       ilyenkor a hasMore azt jelenti, hogy még újabb üzenet is van
     * @param limit oldal méret (max 50)
     * @return az üzenetek listája növekvő időrendben, lapozási metaadatokkal
     * @throws UnauthorizedException ha az igénylőnek nincs jogosultsága az adott csapathoz
     * @throws NotFoundException ha a csapat nem létezik
     */
    @Transactional(readOnly = true)
    public ChatHistoryResponse getHistory(
            UUID teamId, User requester, LocalDateTime beforeCreatedAt, UUID beforeId,
            LocalDateTime afterCreatedAt, UUID afterId, Integer limit) {
        // Jogosultság ellenőrzése
        if (!userAccessService.canAccessTeamChat(requester, teamId)) {
            log.atWarn()
                    .setMessage("User {} attempted to fetch history for team {} without access")
                    .addArgument(requester.getId())
                    .addArgument(teamId)
                    .addKeyValue("event_type", "TEAM_CHAT_HISTORY_UNAUTHORIZED")
                    .addKeyValue("team_id", teamId)
                    .addKeyValue("user_id", requester.getId())
                    .log();
            throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied to team");
        }

        // Csapat meglétének ellenőrzése
        if (!teamRepository.existsById(teamId)) {
            throw new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found: " + teamId);
        }

        int pageSize = resolvePageSize(limit);
        boolean after = afterCreatedAt != null || afterId != null;
        if (after && (afterCreatedAt == null || afterId == null || beforeCreatedAt != null || beforeId != null)) {
            throw new BadRequestException(
                    "error.validation.invalid_request",
                    new Object[]{"afterCreatedAt, afterId"},
                    "afterCreatedAt and afterId go together and cannot be combined with before*"
            );
        }

        // +1 elemet kérünk, hogy ki tudjuk számolni a hasMore értéket.
        PageRequest pageRequest = PageRequest.of(0, pageSize + 1);
        List<TeamMessage> messages = new ArrayList<>(after
                ? teamMessageRepository.findMessagesAfter(teamId, afterCreatedAt, afterId, pageRequest)
                : beforeCreatedAt == null
                ? teamMessageRepository.findLatestMessagesByTeamId(teamId, pageRequest)
                : beforeId == null
                    ? teamMessageRepository.findMessagesBefore(teamId, beforeCreatedAt, pageRequest)
                    : teamMessageRepository.findMessagesBefore(teamId, beforeCreatedAt, beforeId, pageRequest));

        boolean hasMore = messages.size() > pageSize;
        if (hasMore) {
            messages = messages.subList(0, pageSize);
        }

        // Fordított sorrend (hogy időrendben növekvő legyen az eredmény); az "after" lekérdezés már növekvő
        if (!after) {
            Collections.reverse(messages);
        }

        boolean olderCursor = !after && hasMore && !messages.isEmpty();
        LocalDateTime nextBeforeCreatedAt = olderCursor
                ? messages.get(0).getCreatedAt()
                : null;
        UUID nextBeforeId = olderCursor
                ? messages.get(0).getId()
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

        List<ChatMessageResponse> messageResponses = mapMessages(messages);

        return new ChatHistoryResponse(messageResponses, hasMore, nextBeforeCreatedAt, nextBeforeId);
    }

    private Clip shareableClip(User sender, UUID teamId, UUID clipId) {
        Clip clip = clipRepository.findById(clipId)
                .orElseThrow(() -> new NotFoundException("error.clip.not_found", new Object[]{clipId}, "Clip not found: " + clipId));
        userAccessService.requireClipAccess(sender, clipId);
        if (!userAccessService.isClipOfTeam(clipId, teamId)) {
            throw new ForbiddenException("error.chat.clip_not_in_team", new Object[0],
                    "Only a clip of the team's own match can be shared in its chat.");
        }
        if (!ClipService.RENDER_STATUS_COMPLETED.equals(clip.getRenderStatus())) {
            throw new BadRequestException("error.chat.clip_not_ready", new Object[0], "The clip is not rendered yet.");
        }
        return clip;
    }

    /**
     * A csapat chatjében megosztott klipek: a legutóbbi 50 klipüzenetből klipenként a legfrissebb
     * megosztás, legfeljebb 20 klip, a legfrissebb elöl.
     */
    @Transactional(readOnly = true)
    public List<ChatSharedClipResponse> getSharedClips(UUID teamId, User requester) {
        userAccessService.requireTeamChatAccess(requester, teamId);
        List<TeamMessage> latest = teamMessageRepository.findLatestClipMessages(
                teamId, PageRequest.of(0, SHARED_CLIP_MESSAGES_SCANNED));
        Map<UUID, ChatClipSummary> clips = clipSummaries(latest);
        Map<UUID, ChatSharedClipResponse> firstShare = new LinkedHashMap<>();
        for (TeamMessage message : latest) {
            ChatClipSummary clip = clips.get(message.getClip().getId());
            if (clip == null || firstShare.containsKey(clip.id())) {
                continue;
            }
            firstShare.put(clip.id(), new ChatSharedClipResponse(
                    message.getId(), message.getCreatedAt(), message.getSender().getFullName(), clip));
            if (firstShare.size() == SHARED_CLIPS_LISTED) {
                break;
            }
        }
        return List.copyOf(firstShare.values());
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
    private List<ChatMessageResponse> mapMessages(List<TeamMessage> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        UUID teamId = messages.get(0).getTeamId();
        List<UUID> senderIds = messages.stream()
                .map(message -> message.getSender().getId())
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<UUID, List<ChatChildName>> childrenByParent = childNamesOnTeam(teamId, senderIds);
        Map<UUID, ChatClipSummary> clips = clipSummaries(messages);
        Map<UUID, String> roles = rolesOnTeam(teamId, messages);
        return messages.stream()
                .map(message -> mapToResponse(
                        message,
                        childrenByParent.get(message.getSender().getId()),
                        message.getClip() == null ? null : clips.get(message.getClip().getId()),
                        roles.get(message.getSender().getId())))
                .toList();
    }

    /**
     * The role label of each sender on this team, from the database (not from the sender's primary role):
     * ADMIN (platform admin) > CLUB_ADMIN (admin of this team's club) > COACH (coach of this team); others get none.
     * At most two queries per page. It shows the current role, not the one at sending time.
     */
    private Map<UUID, String> rolesOnTeam(UUID teamId, List<TeamMessage> messages) {
        Map<UUID, String> roles = new LinkedHashMap<>();
        Set<UUID> others = new java.util.HashSet<>();
        Set<UUID> platformAdmins = messages.isEmpty() ? Set.of() : platformAdminDirectory.adminIdsForDisplay();
        for (TeamMessage message : messages) {
            User sender = message.getSender();
            if (sender.getId() == null) {
                continue;
            }
            if (platformAdmins.contains(sender.getId())) {
                roles.put(sender.getId(), UserRole.ADMIN.name());
            } else {
                others.add(sender.getId());
            }
        }
        if (teamId == null || others.isEmpty()) {
            return roles;
        }
        for (UUID id : teamRepository.findClubAdminIdsOfTeam(teamId, others)) {
            roles.put(id, UserRole.CLUB_ADMIN.name());
        }
        others.removeAll(roles.keySet());
        if (!others.isEmpty()) {
            for (UUID id : teamRepository.findCoachIdsOfTeam(teamId, others)) {
                roles.put(id, UserRole.COACH.name());
            }
        }
        return roles;
    }

    /** The clips of these messages with their match, in one query (no N+1). */
    private Map<UUID, ChatClipSummary> clipSummaries(List<TeamMessage> messages) {
        Set<UUID> clipIds = messages.stream()
                .map(TeamMessage::getClip)
                .filter(Objects::nonNull)
                .map(Clip::getId)
                .collect(Collectors.toSet());
        if (clipIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ChatClipSummary> summaries = new LinkedHashMap<>();
        for (Clip clip : clipRepository.findAllWithMatchByIdIn(clipIds)) {
            Match match = clip.getMatch();
            summaries.put(clip.getId(), new ChatClipSummary(
                    clip.getId(),
                    clip.getName(),
                    match != null ? match.getId() : null,
                    match != null ? ClipService.buildMatchDisplayName(match) : null));
        }
        return summaries;
    }

    private Map<UUID, List<ChatChildName>> childNamesOnTeam(UUID teamId, Collection<UUID> senderIds) {
        if (teamId == null || senderIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<ChatChildName>> names = new LinkedHashMap<>();
        for (ParentChildName child : playerParentRepository.findChildrenOnTeam(senderIds, teamId)) {
            String firstName = child.firstName() == null ? "" : child.firstName().trim();
            String lastName = child.lastName() == null ? "" : child.lastName().trim();
            if ((firstName.isEmpty() && lastName.isEmpty()) || child.parentId() == null) {
                continue;
            }
            ChatChildName name = new ChatChildName(firstName, lastName);
            names.computeIfAbsent(child.parentId(), ignored -> new ArrayList<>());
            if (!names.get(child.parentId()).contains(name)) {
                names.get(child.parentId()).add(name);
            }
        }
        return names;
    }

    private ChatMessageResponse mapToResponse(
            TeamMessage message, List<ChatChildName> childNames, ChatClipSummary clip, String senderRole) {
        return new ChatMessageResponse(
                message.getId(),
                message.getTeamId(),
                message.getSender().getId(),
                message.getSender().getFullName(),
                message.getSender().getFirstName(),
                message.getSender().getLastName(),
                message.getContent(),
                message.getCreatedAt(),
                childNames == null || childNames.isEmpty() ? null : List.copyOf(childNames),
                senderRole,
                (message.getType() != null ? message.getType() : TeamMessageType.TEXT).name(),
                clip
        );
    }
}
