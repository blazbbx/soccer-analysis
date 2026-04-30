package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.requests.UpdateMatchRequest;
import com.example.footballanalysis.model.requests.UploadMatchRequest;
import com.example.footballanalysis.model.responses.ClipResponse;
import com.example.footballanalysis.model.responses.MatchResponse;
import com.example.footballanalysis.service.ClipService;
import com.example.footballanalysis.service.MatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/matches",produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;
    private final ClipService clipService;

    @GetMapping
    public ResponseEntity<List<MatchResponse>> getAllMatches(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(matchService.getAllMatches(jwt));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MatchResponse> getMatch(@PathVariable UUID id) {
        return ResponseEntity.ok(matchService.getMatchDetails(id));
    }

    @GetMapping("/{matchId}/clips")
    public ResponseEntity<List<ClipResponse>> getClips(@PathVariable UUID matchId, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(clipService.getClipsForMatch(matchId, jwt));
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> initiateUpload(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UploadMatchRequest request) {
        return ResponseEntity.ok(matchService.initiateMatchUpload(request, jwt));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MatchResponse> updateMatch(@PathVariable UUID id,
                                                     @RequestBody UpdateMatchRequest request,
                                                     @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(matchService.updateMatch(id, request, jwt));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMatch(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        matchService.deleteMatch(id, jwt);
        return ResponseEntity.noContent().build();
    }
}
