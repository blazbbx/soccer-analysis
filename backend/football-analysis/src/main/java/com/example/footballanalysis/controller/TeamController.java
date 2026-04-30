package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.requests.CreateTeamRequest;
import com.example.footballanalysis.model.requests.UpdateTeamRequest;
import com.example.footballanalysis.model.responses.TeamResponse;
import com.example.footballanalysis.service.TeamService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Teams", description = "csapatkezelés API végpontjai")
@RestController
@RequestMapping(value = "/api/teams",produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    /**
     * Visszaadja a bejelentkezett felhasználó csapatait.
     *
     * @param jwt a hitelesítési token, amelyből a szolgáltatás meghatározza a felhasználóhoz tartozó csapatokat
     * @return a felhasználóhoz tartozó csapatok listája
     */
    @GetMapping
    public ResponseEntity<List<TeamResponse>> getMyTeams(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(teamService.getMyTeams(jwt));
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
     * @param jwt a hitelesített felhasználó JWT tokenje
     * @return a frissített csapat adatai
     */
    @PutMapping("/{id}")
    public ResponseEntity<TeamResponse> updateTeam(@PathVariable UUID id,
                                                   @Valid @RequestBody UpdateTeamRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(teamService.updateTeam(id, request, jwt));
    }

    /**
     * Csapat törlése.
     *
     * @param id a törlendő csapat azonosítója
     * @param jwt a hitelesített felhasználó JWT tokenje
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
    public ResponseEntity<Void> addPlayer(@PathVariable UUID teamId, @PathVariable UUID playerId, @AuthenticationPrincipal Jwt jwt) {
        teamService.addPlayerToTeam(teamId, playerId, jwt);
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
    public ResponseEntity<Void> removePlayer(@PathVariable UUID teamId, @PathVariable UUID playerId, @AuthenticationPrincipal Jwt jwt) {
        teamService.removePlayerFromTeam(teamId, playerId, jwt);
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
    public ResponseEntity<Void> addCoach(@PathVariable UUID teamId, @PathVariable UUID coachId, @AuthenticationPrincipal Jwt jwt) {
        teamService.addCoachToTeam(teamId, coachId, jwt);
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
    public ResponseEntity<Void> removeCoach(@PathVariable UUID teamId, @PathVariable UUID coachId, @AuthenticationPrincipal Jwt jwt) {
        teamService.removeCoachFromTeam(teamId, coachId, jwt);
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
    public ResponseEntity<Void> addFan(@PathVariable UUID teamId, @PathVariable UUID fanId, @AuthenticationPrincipal Jwt jwt) {
        teamService.addFanToTeam(teamId, fanId, jwt);
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
    public ResponseEntity<Void> removeFan(@PathVariable UUID teamId, @PathVariable UUID fanId, @AuthenticationPrincipal Jwt jwt) {
        teamService.removeFanFromTeam(teamId, fanId, jwt);
        return ResponseEntity.noContent().build();
    }
}
