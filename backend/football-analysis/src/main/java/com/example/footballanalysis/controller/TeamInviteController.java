package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.responses.InviteTokenResponse;
import com.example.footballanalysis.model.responses.TeamInviteResponse;
import com.example.footballanalysis.service.TeamInviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import com.example.footballanalysis.service.UserAccessService;

import java.util.UUID;

@Tag(name = "Team Invitations", description = "csapatmeghívó API végpontjai")
@RestController
@RequestMapping(value="/api",produces = MediaType.APPLICATION_JSON_VALUE)
public class TeamInviteController {

    private final TeamInviteService teamInviteService;
    private final UserAccessService userAccessService;

    /**
     * Konstruktor a TeamInviteController osztályhoz.
     *
     * @param teamInviteService A csapatmeghívókat kezelő szolgáltatás
     */
    public TeamInviteController(TeamInviteService teamInviteService, UserAccessService userAccessService) {
        this.teamInviteService = teamInviteService;
        this.userAccessService = userAccessService;
    }

    /**
    * Meghívó token létrehozása egy adott csapathoz.
     * Csak 'COACH', 'PLAYER' vagy 'ADMIN' jogosultsággal rendelkező felhasználók számára elérhető.
     * A meghívott szerepkör megadása kötelező.
     *
     * @param teamId A csapat egyedi azonosítója (UUID)
     * @param role A meghívott felhasználó szerepköre (opcionális)
     * @param jwt A kérelmet indító hitelesített felhasználó JWT tokenje
     * @return A generált meghívó tokent tartalmazó válasz
     */
    @Operation(summary = "Meghívó token létrehozása egy csapathoz")
    @PostMapping( "/team-invites/{teamId}/invites")
    public ResponseEntity<InviteTokenResponse> createInvite(@PathVariable UUID teamId,
                                                            @RequestParam(name = "role") UserRole role,
                                                            @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return ResponseEntity.ok(teamInviteService.generateInviteToken(teamId, actor, role));
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
        User actor = userAccessService.resolveCurrentUser(jwt);
        return ResponseEntity.ok(teamInviteService.acceptInvite(token, actor));
    }
}