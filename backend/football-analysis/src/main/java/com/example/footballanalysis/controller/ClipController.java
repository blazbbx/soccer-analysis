package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.requests.*;
import com.example.footballanalysis.model.responses.ClipResponse;
import com.example.footballanalysis.model.responses.ClipCompositionUploadResponse;
import com.example.footballanalysis.service.ClipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/clips")
@RequiredArgsConstructor
public class ClipController {

    private final ClipService clipService;


    @PostMapping
    public ResponseEntity<ClipResponse> createClip(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ClipCreateRequest request
    ) {
        return ResponseEntity.ok(clipService.createClip(request.matchId(), request, jwt));
    }

    @PostMapping("/upload-links")
    public ResponseEntity<ClipCompositionUploadResponse> createClipWithUploadLinks(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ClipCreateWithUploadRequest request
    ) {
        return ResponseEntity.ok(clipService.createClipWithUploadLinks(request.matchId(), request, jwt));
    }

//    @PostMapping("/{clipId}/composition/upload")
//    public ResponseEntity<ClipCompositionUploadResponse> initiateCompositionUpload(
//            @PathVariable UUID matchId,
//            @PathVariable UUID clipId,
//            @AuthenticationPrincipal Jwt jwt,
//            @Valid @RequestBody(required = false) ClipCompositionUploadRequest request
//    ) {
//        return ResponseEntity.ok(clipService.initiateCompositionUpload(matchId, clipId, request, jwt));
//    }

    @PostMapping("/{clipId}/composition/complete")
    public ResponseEntity<ClipResponse> completeCompositionUpload(
            @PathVariable UUID clipId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(clipService.completeCompositionUpload(clipId, jwt));
    }

    @GetMapping("/{clipId}/access")
    public ResponseEntity<Map<String, String>> getRenderedClipDownloadUrl(
            @PathVariable UUID clipId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String downloadUrl = clipService.generateRenderedClipDownloadUrl(clipId, jwt);
        return ResponseEntity.ok(Map.of("downloadUrl", downloadUrl));
    }

    @GetMapping("/{clipId}")
    public ResponseEntity<ClipResponse> getClipById(
            @PathVariable UUID clipId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(clipService.getClipById(clipId, jwt));
    }

    @PutMapping("/{clipId}")
    public ResponseEntity<ClipResponse> updateClip(
            @PathVariable UUID clipId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ClipUpdateRequest request
    ) {
        return ResponseEntity.ok(clipService.updateClip(clipId, request, jwt));
    }

    @DeleteMapping("/{clipId}")
    public ResponseEntity<Void> deleteClip(
            @PathVariable UUID clipId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        clipService.deleteClip(clipId, jwt);
        return ResponseEntity.noContent().build();
    }
}