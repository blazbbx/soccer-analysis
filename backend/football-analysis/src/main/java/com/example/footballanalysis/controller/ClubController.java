package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.requests.CreateClubRequest;
import com.example.footballanalysis.model.requests.CreateTeamRequest;
import com.example.footballanalysis.model.requests.UpdateClubRequest;
import com.example.footballanalysis.model.responses.ClubAdminResponse;
import com.example.footballanalysis.model.responses.ClubResponse;
import com.example.footballanalysis.model.responses.PageResponse;
import com.example.footballanalysis.model.responses.InviteTokenResponse;
import com.example.footballanalysis.model.responses.TeamResponse;
import com.example.footballanalysis.model.responses.UserResponse;
import com.example.footballanalysis.service.ClubAdminInviteService;
import com.example.footballanalysis.service.ClubService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Clubs", description = "klubkezelés API végpontjai")
@RestController
@RequestMapping(value = "/api/clubs", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ClubController {

    private final ClubService clubService;
    private final ClubAdminInviteService clubAdminInviteService;

    @GetMapping
    public ResponseEntity<PageResponse<ClubResponse>> listClubs(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(clubService.listClubs(jwt, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClubResponse> getClub(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(clubService.getClub(id, jwt));
    }

    @GetMapping("/{id}/users")
    public ResponseEntity<PageResponse<UserResponse>> listClubUsers(
            @PathVariable UUID id,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(clubService.listClubUsers(id, query, page, size, jwt));
    }

    @GetMapping("/{id}/teams")
    public ResponseEntity<List<TeamResponse>> listClubTeams(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(clubService.listClubTeams(id, jwt));
    }

    @PostMapping("/{id}/teams")
    public ResponseEntity<TeamResponse> createTeamInClub(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTeamRequest request) {
        return ResponseEntity.ok(clubService.createTeamInClub(id, request, jwt));
    }

    @PostMapping("/{id}/admin-invites")
    public ResponseEntity<InviteTokenResponse> createAdminInvite(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(clubAdminInviteService.create(id, jwt));
    }

    @GetMapping("/{id}/admins")
    public ResponseEntity<List<ClubAdminResponse>> listAdmins(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(clubAdminInviteService.list(id, jwt));
    }

    @DeleteMapping("/{id}/admins/{userId}")
    public ResponseEntity<Void> removeAdmin(
            @PathVariable UUID id,
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt) {
        clubAdminInviteService.remove(id, userId, jwt);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Új klub létrehozása")
    @PostMapping
    public ResponseEntity<ClubResponse> createClub(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateClubRequest request) {
        return ResponseEntity.ok(clubService.createClub(request, jwt));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClubResponse> updateClub(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateClubRequest request) {
        return ResponseEntity.ok(clubService.updateClub(id, request, jwt));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClub(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        clubService.deleteClub(id, jwt);
        return ResponseEntity.noContent().build();
    }
}
