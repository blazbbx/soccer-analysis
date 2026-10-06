package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.responses.ChildResponse;
import com.example.footballanalysis.model.responses.MeViewsResponse;
import com.example.footballanalysis.service.MeService;
import com.example.footballanalysis.service.PlayerParentService;
import com.example.footballanalysis.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/me", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class MeController {

    private final UserAccessService userAccessService;
    private final PlayerParentService playerParentService;
    private final MeService meService;

    @GetMapping("/views")
    public MeViewsResponse views(@AuthenticationPrincipal Jwt jwt) {
        return meService.getViews(jwt);
    }

    @GetMapping("/children")
    public List<ChildResponse> children(@AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return playerParentService.listChildren(actor);
    }
}
