package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.requests.BookSeatRequest;
import com.example.footballanalysis.model.requests.UpsertAttendanceRequest;
import com.example.footballanalysis.model.requests.UpsertRideOfferRequest;
import com.example.footballanalysis.model.requests.UpsertTeamEventRequest;
import com.example.footballanalysis.model.responses.TeamEventResponse;
import com.example.footballanalysis.service.TeamEventService;
import com.example.footballanalysis.service.UserAccessService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/teams/{teamId}/events", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Travel", description = "Meccsre lejutás")
public class TeamEventController {

    private final TeamEventService teamEventService;
    private final UserAccessService userAccessService;

    @GetMapping
    public List<TeamEventResponse> list(@PathVariable UUID teamId, @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return teamEventService.listEvents(teamId, actor);
    }

    /** Earlier events, newest first. The cursor is the oldest event the client already has. */
    @GetMapping("/past")
    public List<TeamEventResponse> listPast(
            @PathVariable UUID teamId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime beforeStartsAt,
            @RequestParam(required = false) UUID beforeId,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return teamEventService.listPastEvents(teamId, beforeStartsAt, beforeId, limit, actor);
    }

    @GetMapping("/{eventId}")
    public TeamEventResponse get(
            @PathVariable UUID teamId,
            @PathVariable UUID eventId,
            @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return teamEventService.getEvent(teamId, eventId, actor);
    }

    @PostMapping
    public ResponseEntity<TeamEventResponse> create(
            @PathVariable UUID teamId,
            @Valid @RequestBody UpsertTeamEventRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return ResponseEntity.ok(teamEventService.createEvent(teamId, request, actor));
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<TeamEventResponse> update(
            @PathVariable UUID teamId,
            @PathVariable UUID eventId,
            @Valid @RequestBody UpsertTeamEventRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return ResponseEntity.ok(teamEventService.updateEvent(teamId, eventId, request, actor));
    }

    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID teamId,
            @PathVariable UUID eventId,
            @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        teamEventService.deleteEvent(teamId, eventId, actor);
    }

    @PutMapping("/{eventId}/attendance/{playerProfileId}")
    public ResponseEntity<TeamEventResponse> attendance(
            @PathVariable UUID teamId,
            @PathVariable UUID eventId,
            @PathVariable UUID playerProfileId,
            @Valid @RequestBody UpsertAttendanceRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return ResponseEntity.ok(teamEventService.upsertAttendance(teamId, eventId, playerProfileId, request, actor));
    }

    @PostMapping("/{eventId}/rides")
    public ResponseEntity<TeamEventResponse> createRide(
            @PathVariable UUID teamId,
            @PathVariable UUID eventId,
            @Valid @RequestBody UpsertRideOfferRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return ResponseEntity.ok(teamEventService.createRide(teamId, eventId, request, actor));
    }

    @PutMapping("/{eventId}/rides/{offerId}")
    public ResponseEntity<TeamEventResponse> updateRide(
            @PathVariable UUID teamId,
            @PathVariable UUID eventId,
            @PathVariable UUID offerId,
            @Valid @RequestBody UpsertRideOfferRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return ResponseEntity.ok(teamEventService.updateRide(teamId, eventId, offerId, request, actor));
    }

    @DeleteMapping("/{eventId}/rides/{offerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRide(
            @PathVariable UUID teamId,
            @PathVariable UUID eventId,
            @PathVariable UUID offerId,
            @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        teamEventService.deleteRide(teamId, eventId, offerId, actor);
    }

    @PostMapping("/{eventId}/rides/{offerId}/seats")
    public ResponseEntity<TeamEventResponse> bookSeat(
            @PathVariable UUID teamId,
            @PathVariable UUID eventId,
            @PathVariable UUID offerId,
            @Valid @RequestBody BookSeatRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return ResponseEntity.ok(teamEventService.bookSeat(teamId, eventId, offerId, request, actor));
    }

    @DeleteMapping("/{eventId}/rides/{offerId}/seats/{playerProfileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void releaseSeat(
            @PathVariable UUID teamId,
            @PathVariable UUID eventId,
            @PathVariable UUID offerId,
            @PathVariable UUID playerProfileId,
            @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        teamEventService.releaseSeat(teamId, eventId, offerId, playerProfileId, actor);
    }
}
