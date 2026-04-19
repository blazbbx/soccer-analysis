package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.requests.ClipUpdateRequest;
import com.example.footballanalysis.model.requests.ClipUploadRequest;
import com.example.footballanalysis.model.responses.ClipResponse;
import com.example.footballanalysis.model.responses.ClipUploadResponse;
import com.example.footballanalysis.service.ClipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/matches/{matchId}/clips")
@RequiredArgsConstructor
public class ClipController {

    private final ClipService clipService;

    @GetMapping
    public ResponseEntity<List<ClipResponse>> getClips(@PathVariable UUID matchId) {
        return ResponseEntity.ok(clipService.getClipsForMatch(matchId));
    }

    @PostMapping("/upload")
    public ResponseEntity<ClipUploadResponse> initiateUpload(
            @PathVariable UUID matchId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ClipUploadRequest request
    ) {
        return ResponseEntity.ok(clipService.initiateClipUpload(matchId, request, jwt));
    }

    @PutMapping("/{clipId}")
    public ResponseEntity<ClipResponse> updateClip(
            @PathVariable UUID matchId,
            @PathVariable UUID clipId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ClipUpdateRequest request
    ) {
        return ResponseEntity.ok(clipService.updateClip(matchId, clipId, request, jwt));
    }

    @DeleteMapping("/{clipId}")
    public ResponseEntity<Void> deleteClip(
            @PathVariable UUID matchId,
            @PathVariable UUID clipId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        clipService.deleteClip(matchId, clipId, jwt);
        return ResponseEntity.noContent().build();
    }
}