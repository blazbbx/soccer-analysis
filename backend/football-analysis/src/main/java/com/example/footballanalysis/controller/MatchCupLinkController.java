package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.responses.MatchCupLinkResponse;
import com.example.footballanalysis.service.CupService;
import com.example.footballanalysis.service.UserAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** The cups an analysed match belongs to (chip on the match page). */
@Tag(name = "Cups", description = "Kupa kezelés API végpontjai")
@RestController
@RequestMapping(value = "/api/matches", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class MatchCupLinkController {

    private final CupService cupService;
    private final UserAccessService userAccessService;

    @Operation(summary = "Az elemzett meccshez kötött kupameccsek")
    @GetMapping("/{matchId}/cup-matches")
    public ResponseEntity<List<MatchCupLinkResponse>> getCupMatchesOfMatch(
            @PathVariable UUID matchId,
            @RequestParam(name = "view", required = false) UserRole view,
            @AuthenticationPrincipal Jwt jwt) {
        User user = userAccessService.resolveCurrentUser(jwt);
        return ResponseEntity.ok(cupService.listCupLinksForMatch(matchId, user, view));
    }
}
