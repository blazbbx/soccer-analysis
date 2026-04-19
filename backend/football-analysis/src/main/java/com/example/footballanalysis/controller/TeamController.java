package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.requests.CreateTeamRequest;
import com.example.footballanalysis.model.requests.UpdateTeamRequest;
import com.example.footballanalysis.model.responses.TeamResponse;
import com.example.footballanalysis.service.TeamService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Teams", description = "csapatkezelés API végpontjai")
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    /**
     * Visszaadja az összes csapatot.
     *
     * @return a csapatok listája
     */
    @GetMapping
    public ResponseEntity<List<TeamResponse>> getAllTeams() {
        return ResponseEntity.ok(teamService.getAllTeams());
    }

    /**
     * Visszaadja a bejelentkezett felhasználó csapatait.
     *
     * @param authentication az aktuális felhasználó hitelesítési adatai
     * @return a felhasználóhoz tartozó csapatok listája
     */
    @GetMapping("/me")
    public ResponseEntity<List<TeamResponse>> getMyTeams(Authentication authentication) {
        return ResponseEntity.ok(teamService.getMyTeams(authentication));
    }

    /**
     * Egy adott csapat lekérdezése azonosító alapján.
     *
     * @param id a lekérdezni kívánt csapat egyedi azonosítója
     * @return a lekérdezett csapat adatai
     */
    @GetMapping("/{id}")
    public ResponseEntity<TeamResponse> getTeam(@PathVariable UUID id) {
        return ResponseEntity.ok(teamService.getTeam(id));
    }

    /**
     * Új csapat létrehozása.
     *
     * @param jwt a hitelesítési token
     * @param request az új csapat létrehozásához szükséges adatok
     * @return a létrehozott csapat adatai
     */
    @Operation(summary = "Új csapat létrehozása")
    @PostMapping
    public ResponseEntity<TeamResponse> createTeam(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateTeamRequest request) {
        return ResponseEntity.ok(jwt == null ? teamService.createTeam(request) : teamService.createTeam(request, jwt));
    }

    /**
     * Meglévő csapat adatainak frissítése.
     *
     * @param id a frissíteni kívánt csapat azonosítója
     * @param request a frissítendő adatok
     * @return a frissített csapat adatai
     */
    @PutMapping("/{id}")
    public ResponseEntity<TeamResponse> updateTeam(@PathVariable UUID id, @Valid @RequestBody UpdateTeamRequest request) {
        return ResponseEntity.ok(teamService.updateTeam(id, request));
    }

    /**
     * Csapat törlése.
     *
     * @param id a törlendő csapat azonosítója
     * @return üres válasz a sikeres törlés után
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTeam(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        teamService.deleteTeam(id, jwt);
        return ResponseEntity.noContent().build();
    }

    // ── Játékos kezelés ───────────────────────────────────────────────────────

    /**
     * Játékos hozzáadása a csapathoz.
     *
     * @param teamId a csapat azonosítója
     * @param playerId a hozzáadandó játékos azonosítója
     * @return üres válasz a sikeres hozzáadás után
     */
    @PostMapping("/{teamId}/players/{playerId}")
    public ResponseEntity<Void> addPlayer(@PathVariable UUID teamId, @PathVariable UUID playerId) {
        teamService.addPlayerToTeam(teamId, playerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Játékos eltávolítása a csapatból.
     *
     * @param teamId a csapat azonosítója
     * @param playerId az eltávolítandó játékos azonosítója
     * @return üres válasz a sikeres eltávolítás után
     */
    @DeleteMapping("/{teamId}/players/{playerId}")
    public ResponseEntity<Void> removePlayer(@PathVariable UUID teamId, @PathVariable UUID playerId) {
        teamService.removePlayerFromTeam(teamId, playerId);
        return ResponseEntity.noContent().build();
    }

    // ── Coach kezelés ─────────────────────────────────────────────────────────

    /**
     * Edző hozzáadása a csapathoz.
     *
     * @param teamId a csapat azonosítója
     * @param coachId a hozzáadandó edző azonosítója
     * @return üres válasz a sikeres hozzáadás után
     */
    @PostMapping("/{teamId}/coaches/{coachId}")
    public ResponseEntity<Void> addCoach(@PathVariable UUID teamId, @PathVariable UUID coachId) {
        teamService.addCoachToTeam(teamId, coachId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Edző eltávolítása a csapatból.
     *
     * @param teamId a csapat azonosítója
     * @param coachId az eltávolítandó edző azonosítója
     * @return üres válasz a sikeres eltávolítás után
     */
    @DeleteMapping("/{teamId}/coaches/{coachId}")
    public ResponseEntity<Void> removeCoach(@PathVariable UUID teamId, @PathVariable UUID coachId) {
        teamService.removeCoachFromTeam(teamId, coachId);
        return ResponseEntity.noContent().build();
    }

    // ── Fan kezelés ─────────────────────────────────────────────────────────

    /**
     * Szurkoló hozzáadása a csapathoz.
     *
     * @param teamId a csapat azonosítója
     * @param fanId a hozzáadandó szurkoló azonosítója
     * @return üres válasz a sikeres hozzáadás után
     */
    @PostMapping("/{teamId}/fans/{fanId}")
    public ResponseEntity<Void> addFan(@PathVariable UUID teamId, @PathVariable UUID fanId) {
        teamService.addFanToTeam(teamId, fanId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Szurkoló eltávolítása a csapatból.
     *
     * @param teamId a csapat azonosítója
     * @param fanId az eltávolítandó szurkoló azonosítója
     * @return üres válasz a sikeres eltávolítás után
     */
    @DeleteMapping("/{teamId}/fans/{fanId}")
    public ResponseEntity<Void> removeFan(@PathVariable UUID teamId, @PathVariable UUID fanId) {
        teamService.removeFanFromTeam(teamId, fanId);
        return ResponseEntity.noContent().build();
    }
}
