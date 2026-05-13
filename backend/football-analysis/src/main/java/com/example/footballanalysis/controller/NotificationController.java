package com.example.footballanalysis.controller;

import com.example.footballanalysis.service.SseNotificationService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.service.UserAccessService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final SseNotificationService sseService;
    private final UserAccessService userAccessService;

    public NotificationController(SseNotificationService sseService, UserAccessService userAccessService) {
        this.sseService = sseService;
        this.userAccessService = userAccessService;
    }

    // React will call this using EventSource
    @GetMapping(value = "/subscribe/{matchId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable String matchId, @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return sseService.subscribe(matchId, actor);
    }
}
