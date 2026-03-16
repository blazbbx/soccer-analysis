package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.requests.CreateTeamRequest;
import com.example.footballanalysis.model.requests.UpdateTeamRequest;
import com.example.footballanalysis.model.responses.TeamResponse;
import com.example.footballanalysis.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    public ResponseEntity<List<TeamResponse>> getAllTeams() {
        return ResponseEntity.ok(teamService.getAllTeams());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeamResponse> getTeam(@PathVariable UUID id) {
        return ResponseEntity.ok(teamService.getTeam(id));
    }

    @PostMapping
    public ResponseEntity<TeamResponse> createTeam(@Valid @RequestBody CreateTeamRequest request) {
        return ResponseEntity.ok(teamService.createTeam(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TeamResponse> updateTeam(@PathVariable UUID id, @Valid @RequestBody UpdateTeamRequest request) {
        return ResponseEntity.ok(teamService.updateTeam(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTeam(@PathVariable UUID id) {
        teamService.deleteTeam(id);
        return ResponseEntity.noContent().build();
    }

    // ── Játékos kezelés ───────────────────────────────────────────────────────

    @PostMapping("/{teamId}/players/{playerId}")
    public ResponseEntity<Void> addPlayer(@PathVariable UUID teamId, @PathVariable UUID playerId) {
        teamService.addPlayerToTeam(teamId, playerId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{teamId}/players/{playerId}")
    public ResponseEntity<Void> removePlayer(@PathVariable UUID teamId, @PathVariable UUID playerId) {
        teamService.removePlayerFromTeam(teamId, playerId);
        return ResponseEntity.noContent().build();
    }

    // ── Coach kezelés ─────────────────────────────────────────────────────────

    @PostMapping("/{teamId}/coaches/{coachId}")
    public ResponseEntity<Void> addCoach(@PathVariable UUID teamId, @PathVariable UUID coachId) {
        teamService.addCoachToTeam(teamId, coachId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{teamId}/coaches/{coachId}")
    public ResponseEntity<Void> removeCoach(@PathVariable UUID teamId, @PathVariable UUID coachId) {
        teamService.removeCoachFromTeam(teamId, coachId);
        return ResponseEntity.noContent().build();
    }

    // ── Fan kezelés ─────────────────────────────────────────────────────────

    @PostMapping("/{teamId}/fans/{fanId}")
    public ResponseEntity<Void> addFan(@PathVariable UUID teamId, @PathVariable UUID fanId) {
        teamService.addFanToTeam(teamId, fanId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{teamId}/fans/{fanId}")
    public ResponseEntity<Void> removeFan(@PathVariable UUID teamId, @PathVariable UUID fanId) {
        teamService.removeFanFromTeam(teamId, fanId);
        return ResponseEntity.noContent().build();
    }
}
