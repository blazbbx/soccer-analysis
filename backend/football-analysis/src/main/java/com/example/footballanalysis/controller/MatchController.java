package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.requests.UploadMatchRequest;
import com.example.footballanalysis.model.responses.MatchResponse;
import com.example.footballanalysis.service.MatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @GetMapping
    public ResponseEntity<List<MatchResponse>> getAllMatches() {
        return ResponseEntity.ok(matchService.getAllMatches());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MatchResponse> getMatch(@PathVariable UUID id) {
        return ResponseEntity.ok(matchService.getMatchDetails(id));
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> initiateUpload(@Valid @RequestBody UploadMatchRequest request) {
        return ResponseEntity.ok(matchService.initiateMatchUpload(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMatch(@PathVariable UUID id) {
        matchService.deleteMatch(id);
        return ResponseEntity.noContent().build();
    }
}
