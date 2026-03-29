package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.responses.InviteLinkResponse;
import com.example.footballanalysis.model.responses.TeamInviteResponse;
import com.example.footballanalysis.service.TeamInviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

@Tag(name = "Team Invitations", description = "csapatmeghívó API végpontjai")
@RestController
@RequestMapping(value="/api",produces = MediaType.APPLICATION_JSON_VALUE)
public class TeamInviteController {

    private final TeamInviteService teamInviteService;

    /**
     * Konstruktor a TeamInviteController osztályhoz.
     *
     * @param teamInviteService A csapatmeghívókat kezelő szolgáltatás
     */
    public TeamInviteController(TeamInviteService teamInviteService) {
        this.teamInviteService = teamInviteService;
    }

    /**
     * Meghívó link létrehozása egy adott csapathoz.
     * Csak 'COACH' vagy 'ADMIN' jogosultsággal rendelkező felhasználók számára elérhető.
     *
     * @param teamId A csapat egyedi azonosítója (UUID)
     * @param role A meghívott felhasználó szerepköre (opcionális)
     * @param jwt A kérelmet indító hitelesített felhasználó JWT tokenje
     * @return A generált meghívó linket tartalmazó válasz
     */
    @Operation(summary = "Meghívó link létrehozása egy csapathoz")
    @PostMapping({"/v1/teams/{teamId}/invites", "/team-invites/teams/{teamId}/invites"})
    @PreAuthorize("hasAnyRole('COACH', 'ADMIN')")
    public ResponseEntity<InviteLinkResponse> createInvite(@PathVariable UUID teamId,
                                                           @RequestParam(name = "role", required = false) UserRole role,
                                                           @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(teamInviteService.generateInviteLink(teamId, jwt, role));
    }

    /**
     * Meghívó link részleteinek és érvényességének lekérése a megadott token alapján.
     *
     * @param token A meghívóhoz tartozó egyedi biztonsági token
     * @return A meghívó adatait és érvényességét tartalmazó válasz
     */
    @Operation(summary = "Meghívó link részleteinek lekérése")
    @GetMapping("/team-invites/{token}")
    public ResponseEntity<TeamInviteResponse> getInvite(@PathVariable String token) {
        return ResponseEntity.ok(teamInviteService.getInvite(token));
    }

    /**
     * Csatlakozás a csapathoz egy érvényes meghívó link (token) elfogadásával.
     *
     * @param token A meghívóhoz tartozó egyedi biztonsági token
     * @param jwt A hitelesített, újonnan csatlakozó felhasználó JWT tokenje
     * @return A sikeres csatlakozás részleteit és a csapat adatait tartalmazó válasz
     */
    @Operation(summary = "Csatlakozás meghívó linkkel")
    @PostMapping("/team-invites/{token}/accept")
    public ResponseEntity<TeamInviteResponse> acceptInvite(@PathVariable String token,
                                                           @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(teamInviteService.acceptInvite(token, jwt));
    }
}