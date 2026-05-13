package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.requests.*;
import com.example.footballanalysis.model.responses.*;
import com.example.footballanalysis.service.CupService;
import com.example.footballanalysis.service.CupStandingsService;
import com.example.footballanalysis.service.UserAccessService;
import com.example.footballanalysis.model.db.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Cups", description = "Kupa kezelés API végpontjai")
@RestController
@RequestMapping(value = "/api/cups", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class CupController {

    private final CupService cupService;
    private final CupStandingsService cupStandingsService;
    private final UserAccessService userAccessService;

    /**
     * Új kupa létrehozása
     */
    @Operation(summary = "Új kupa létrehozása")
    @PostMapping
    public ResponseEntity<CupResponse> createCup(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCupRequest request) {
                User user = userAccessService.resolveCurrentUser(jwt);
        CupResponse response = cupService.createCup(request, user);
        return ResponseEntity.ok(response);
    }

    /**
     * Kupa lekérése azonosító alapján
     */
    @Operation(summary = "Kupa lekérése")
    @GetMapping("/{cupId}")
    public ResponseEntity<CupResponse> getCup(@PathVariable UUID cupId) {
        CupResponse response = cupService.getCup(cupId);
        return ResponseEntity.ok(response);
    }

    /**
     * Az összes kupa lekérése
     */
    @Operation(summary = "Az összes kupa lekérése")
    @GetMapping
    public ResponseEntity<List<CupResponse>> getAllCups() {
        List<CupResponse> cups = cupService.listAllCups();
        return ResponseEntity.ok(cups);
    }

    /**
     * Csapat hozzáadása a kupához
     */
    @Operation(summary = "Csapat hozzáadása a kupához")
    @PostMapping("/{cupId}/teams")
    public ResponseEntity<CupTeamResponse> addTeamToCup(
            @PathVariable UUID cupId,
            @Valid @RequestBody CupTeamCreateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
                User user = userAccessService.resolveCurrentUser(jwt);
        CupTeamResponse response = cupService.addTeamToCup(cupId, request, user);
        return ResponseEntity.ok(response);
    }

    /**
     * Kupacsapat módosítása
     */
    @Operation(summary = "Kupacsapat módosítása")
    @PutMapping("/{cupId}/teams/{teamId}")
    public ResponseEntity<CupTeamResponse> updateTeam(
            @PathVariable UUID cupId,
            @PathVariable UUID teamId,
            @Valid @RequestBody CupTeamCreateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
                User user = userAccessService.resolveCurrentUser(jwt);
        CupTeamResponse response = cupService.updateTeam(cupId, teamId, request, user);
        return ResponseEntity.ok(response);
    }

    /**
     * Kupacsapat törlése
     */
    @Operation(summary = "Kupacsapat törlése")
    @DeleteMapping("/{cupId}/teams/{teamId}")
    public ResponseEntity<Void> deleteTeam(
            @PathVariable UUID cupId,
            @PathVariable UUID teamId,
            @AuthenticationPrincipal Jwt jwt) {
                User user = userAccessService.resolveCurrentUser(jwt);
        cupService.deleteTeam(cupId, teamId, user);
        return ResponseEntity.noContent().build();
    }

    /**
     * Meccs ütemezése a kupához
     */
    @Operation(summary = "Meccs ütemezése")
    @PostMapping("/{cupId}/matches")
    public ResponseEntity<CupMatchResponse> scheduleMatch(
            @PathVariable UUID cupId,
            @Valid @RequestBody CupMatchScheduleRequest request,
            @AuthenticationPrincipal Jwt jwt) {
                User user = userAccessService.resolveCurrentUser(jwt);
        CupMatchResponse response = cupService.scheduleMatch(cupId, request, user);
        return ResponseEntity.ok(response);
    }

    /**
     * Meccs módosítása
     */
    @Operation(summary = "Meccs módosítása")
    @PutMapping("/{cupId}/matches/{matchId}")
    public ResponseEntity<CupMatchResponse> updateMatch(
            @PathVariable UUID cupId,
            @PathVariable UUID matchId,
            @Valid @RequestBody CupMatchScheduleRequest request,
            @AuthenticationPrincipal Jwt jwt) {
                User user = userAccessService.resolveCurrentUser(jwt);
        CupMatchResponse response = cupService.updateMatch(cupId, matchId, request, user);
        return ResponseEntity.ok(response);
    }

    /**
     * Meccs törlése
     */
    @Operation(summary = "Meccs törlése")
    @DeleteMapping("/{cupId}/matches/{matchId}")
    public ResponseEntity<Void> deleteMatch(
            @PathVariable UUID cupId,
            @PathVariable UUID matchId,
            @AuthenticationPrincipal Jwt jwt) {
                User user = userAccessService.resolveCurrentUser(jwt);
        cupService.deleteMatch(cupId, matchId, user);
        return ResponseEntity.noContent().build();
    }

    /**
     * Meccs eredményének rögzítése
     */
    @Operation(summary = "Meccs eredményének rögzítése")
    @PutMapping("/{cupId}/matches/{matchId}/score")
    public ResponseEntity<CupMatchResponse> recordScore(
            @PathVariable UUID cupId,
            @PathVariable UUID matchId,
            @Valid @RequestBody UpdateMatchScoreRequest request,
            @AuthenticationPrincipal Jwt jwt) {
                User user = userAccessService.resolveCurrentUser(jwt);
        CupMatchResponse response = cupService.recordScore(cupId, matchId, request, user);
        return ResponseEntity.ok(response);
    }

    /**
     * Valós meccs összekapcsolása a Cup meccshez
     */
    @Operation(summary = "Valós meccs összekapcsolása")
    @PutMapping("/{cupId}/matches/{matchId}/real-match")
    public ResponseEntity<CupMatchResponse> linkRealMatch(
            @PathVariable UUID cupId,
            @PathVariable UUID matchId,
            @RequestParam UUID realMatchId,
            @AuthenticationPrincipal Jwt jwt) {
                User user = userAccessService.resolveCurrentUser(jwt);
        CupMatchResponse response = cupService.linkRealMatch(cupId, matchId, realMatchId, user);
        return ResponseEntity.ok(response);
    }

    /**
     * Valós meccs unlink (eltávolítás -> null)
     */
    @Operation(summary = "Valós meccs unlink")
    @DeleteMapping("/{cupId}/matches/{matchId}/real-match")
    public ResponseEntity<CupMatchResponse> unlinkRealMatch(
            @PathVariable UUID cupId,
            @PathVariable UUID matchId,
            @AuthenticationPrincipal Jwt jwt) {
                User user = userAccessService.resolveCurrentUser(jwt);
        CupMatchResponse response = cupService.unlinkRealMatch(cupId, matchId, user);
        return ResponseEntity.ok(response);
    }

    /**
     * Kupa tabellájának lekérése
     */
    @Operation(summary = "Kupa tabellájának lekérése")
    @GetMapping("/{cupId}/standings")
    public ResponseEntity<List<CupStandingsRowResponse>> getStandings(@PathVariable UUID cupId) {
        List<CupStandingsRowResponse> standings = cupStandingsService.calculateStandings(cupId);
        return ResponseEntity.ok(standings);
    }

    /**
     * Cup összes mérkőzésének lekérése
     */
    @Operation(summary = "Cup mérkőzéseinek lekérése")
    @GetMapping("/{cupId}/matches")
    public ResponseEntity<List<CupMatchResponse>> getMatches(@PathVariable UUID cupId) {
        CupResponse cupResponse = cupService.getCup(cupId);
        List<CupMatchResponse> matches = cupResponse.matches().stream().toList();
        return ResponseEntity.ok(matches);
    }

    /**
     * Cup összes csapatának lekérése
     */
    @Operation(summary = "Cup csapatainak lekérése")
    @GetMapping("/{cupId}/teams")
    public ResponseEntity<List<CupTeamResponse>> getTeams(@PathVariable UUID cupId) {
        CupResponse cupResponse = cupService.getCup(cupId);
        List<CupTeamResponse> teams = cupResponse.teams().stream().toList();
        return ResponseEntity.ok(teams);
    }

}