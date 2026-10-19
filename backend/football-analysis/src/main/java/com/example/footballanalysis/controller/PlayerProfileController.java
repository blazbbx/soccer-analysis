package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.requests.CreateChildLoginRequest;
import com.example.footballanalysis.model.requests.SetRideOfferRightRequest;
import com.example.footballanalysis.model.responses.ChildResponse;
import com.example.footballanalysis.model.responses.InviteTokenResponse;
import com.example.footballanalysis.model.responses.ParentInviteResponse;
import com.example.footballanalysis.model.responses.ProfileParentResponse;
import com.example.footballanalysis.model.responses.UserResponse;
import com.example.footballanalysis.service.ParentInviteService;
import com.example.footballanalysis.service.PlayerParentService;
import com.example.footballanalysis.service.TeamInviteService;
import com.example.footballanalysis.service.UserAccessService;
import com.example.footballanalysis.service.UserRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/player-profiles", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PlayerProfileController {

    private final TeamInviteService teamInviteService;
    private final UserRegistrationService userRegistrationService;
    private final UserAccessService userAccessService;
    private final PlayerParentService playerParentService;
    private final ParentInviteService parentInviteService;

    @PutMapping("/{id}/ride-offer-right")
    public ChildResponse setRideOfferRight(
            @PathVariable UUID id,
            @Valid @RequestBody SetRideOfferRightRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return playerParentService.setRideOfferRight(id, request, actor);
    }

    @PostMapping("/{id}/child-login-invites")
    public InviteTokenResponse createChildLoginInvite(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return teamInviteService.createChildLoginInvite(id, actor);
    }

    @PostMapping(value = "/{id}/child-login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createChildLogin(
            @PathVariable UUID id,
            @Valid @RequestBody CreateChildLoginRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return userRegistrationService.createChildLogin(id, request, actor);
    }

    @PostMapping("/{id}/parent-invites")
    public InviteTokenResponse createParentInvite(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return parentInviteService.create(id, actor);
    }

    @GetMapping("/{id}/parent-invites")
    public List<ParentInviteResponse> listParentInvites(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return parentInviteService.list(id, actor);
    }

    @DeleteMapping("/{id}/parent-invites/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeParentInvite(
            @PathVariable UUID id,
            @PathVariable UUID inviteId,
            @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        parentInviteService.revoke(id, inviteId, actor);
    }

    @GetMapping("/{id}/parents")
    public List<ProfileParentResponse> listParents(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return playerParentService.listParents(id, actor);
    }

    @DeleteMapping("/{id}/parents/{parentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkParent(
            @PathVariable UUID id,
            @PathVariable UUID parentId,
            @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        playerParentService.unlinkParent(id, parentId, actor);
    }

    @DeleteMapping("/{id}/child-login")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void detachChildLogin(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        playerParentService.detachChildLogin(id, actor);
    }
}
