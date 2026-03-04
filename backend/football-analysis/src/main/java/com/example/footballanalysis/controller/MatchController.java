package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.requests.UploadMatchRequest;
import com.example.footballanalysis.model.responses.MatchResponse;
import com.example.footballanalysis.service.MatchService;
import com.example.footballanalysis.service.S3PresignerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;
    private final S3PresignerService storageService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> getUploadUrl(@RequestBody UploadMatchRequest request) {
        Map<String, String> response = matchService.initiateMatchUpload(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MatchResponse> getMatch(@PathVariable UUID id) {
        MatchResponse response = matchService.getMatchDetails(id);
        return ResponseEntity.ok(response);
    }
}