package com.example.footballanalysis.controller;

import com.example.footballanalysis.service.SseNotificationService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final SseNotificationService sseService;

    public NotificationController(SseNotificationService sseService) {
        this.sseService = sseService;
    }

    // React will call this using EventSource
    @GetMapping(value = "/subscribe/{matchId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable String matchId, @AuthenticationPrincipal Jwt jwt) {
        return sseService.subscribe(matchId, jwt);
    }
}
