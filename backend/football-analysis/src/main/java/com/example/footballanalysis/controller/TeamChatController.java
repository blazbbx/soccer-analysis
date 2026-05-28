package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.requests.ChatMessageRequest;
import com.example.footballanalysis.model.responses.ChatHistoryResponse;
import com.example.footballanalysis.model.responses.ChatMessageResponse;
import com.example.footballanalysis.service.ChatService;
import com.example.footballanalysis.service.SseNotificationService;
import com.example.footballanalysis.service.UserAccessService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Tag(name = "Team Chat", description = "Csapat chat API végpontjai")
@RestController
@RequestMapping(value = "/api/teams", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
public class TeamChatController {

    private final ChatService chatService;
    private final SseNotificationService sseNotificationService;
    private final UserAccessService userAccessService;

    /**
     * Üzenet küldése az adott csapatnak.
     *
     * @param teamId a csapat azonosítója
     * @param request az üzenet tartalma
     * @param jwt a hitelesítési token az aktuális felhasználó azonosításához
     * @return a küldött üzenet DTO-ja
     */
    @Operation(summary = "Üzenet küldése a csapat chatjéhez")
    @PostMapping("/{teamId}/chat")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable UUID teamId,
            @Valid @RequestBody ChatMessageRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User sender = userAccessService.resolveCurrentUser(jwt);
        ChatMessageResponse response = chatService.sendMessage(teamId, sender, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Csapat üzenetélőzményeinek lekérése.
     * Első hívásra az utolsó 50 üzenetet adja vissza.
     * Infinite scroll esetén a kliens a nextBeforeCreatedAt kurzort küldi vissza.
     *
     * @param teamId a csapat azonosítója
     * @param beforeCreatedAt kurzor: ennél régebbi üzeneteket kérünk
     * @param limit lapméret (1-50)
     * @param jwt a hitelesítési token az aktuális felhasználó azonosításához
     * @return lapozott üzenetlista metaadatokkal
     */
    @Operation(summary = "Csapat üzenetélőzményeinek lekérése")
    @GetMapping("/{teamId}/chat")
    public ResponseEntity<ChatHistoryResponse> getHistory(
            @PathVariable UUID teamId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime beforeCreatedAt,
            @RequestParam(defaultValue = "50")
            @Min(1)
            @Max(50)
            Integer limit,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User requester = userAccessService.resolveCurrentUser(jwt);
        ChatHistoryResponse history = chatService.getHistory(teamId, requester, beforeCreatedAt, limit);
        return ResponseEntity.ok(history);
    }

    /**
     * Team chat SSE subscription endpoint.
     * A kliens ezt az endpoint-et használja az SSE kapcsolat létrehozásához.
     *
     * @param jwt a hitelesítési token az aktuális felhasználó azonosításához
     * @return az SseEmitter objektum az real-time üzenetek fogadásához
     */
    @Operation(summary = "Team chat SSE kapcsolat létrehozása")
    @GetMapping("/chat/subscribe")
    public SseEmitter subscribeToTeamChat(
            @AuthenticationPrincipal Jwt jwt
    ) {
        User user = userAccessService.resolveCurrentUser(jwt);
        return sseNotificationService.subscribeToTeamChat(user);
    }

}




