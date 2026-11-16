package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.requests.ConfirmCornersRequest;
import com.example.footballanalysis.model.requests.UpdateMatchRequest;
import com.example.footballanalysis.model.requests.UpdateTrackingLabelDataRequest;
import com.example.footballanalysis.model.requests.UploadMatchRequest;
import com.example.footballanalysis.model.responses.ClipResponse;
import com.example.footballanalysis.model.responses.MatchResponse;
import com.example.footballanalysis.model.responses.PageResponse;
import com.example.footballanalysis.service.ClipService;
import com.example.footballanalysis.service.MatchIngestionOrchestrator;
import com.example.footballanalysis.service.MatchMediaUrls;
import com.example.footballanalysis.service.MatchService;
import com.example.footballanalysis.service.UserAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;

@RestController
@RequestMapping(value = "/api/matches",produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;
    private final ClipService clipService;
    private final UserAccessService userAccessService;
    private final MatchIngestionOrchestrator ingestionOrchestrator;
    private final MatchMediaUrls matchMediaUrls;

    @GetMapping
    public ResponseEntity<PageResponse<MatchResponse>> getAllMatches(
            @RequestParam(name = "view", required = false) UserRole view,
            @RequestParam(name = "teamId", required = false) List<UUID> teamIds,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return ResponseEntity.ok(matchService.getAllMatches(actor, view, teamIds, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MatchResponse> getMatch(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return ResponseEntity.ok(matchService.getMatchDetails(id, actor));
    }

    /**
     * HLS playlist of the match with presigned segment URLs. No login header: the {@code token} in the URL,
     * which only the match response gives to someone who may see the match, is the access check, so any
     * player can open it (hls.js and the native iOS player alike).
     */
    @GetMapping(value = "/{id}/hls/playlist.m3u8", produces = "application/vnd.apple.mpegurl")
    public ResponseEntity<String> getHlsPlaylist(@PathVariable UUID id, @RequestParam("token") String token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(matchMediaUrls.signedPlaylist(id, token));
    }

    @GetMapping("/{matchId}/clips")
    public ResponseEntity<PageResponse<ClipResponse>> getClips(
            @PathVariable UUID matchId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return ResponseEntity.ok(clipService.getClipsForMatch(matchId, actor, page, size));
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> initiateUpload(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UploadMatchRequest request) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return ResponseEntity.ok(matchService.initiateMatchUpload(request, actor));
    }

    @PostMapping("/{id}/confirm-corners")
    public ResponseEntity<MatchResponse> confirmCorners(@PathVariable UUID id,
                                                        @Valid @RequestBody ConfirmCornersRequest request,
                                                        @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        ingestionOrchestrator.confirmCornersAndDispatch(id, request.corners(), actor);
        return ResponseEntity.ok(matchService.getMatchDetails(id, actor));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MatchResponse> updateMatch(@PathVariable UUID id,
                                                     @RequestBody UpdateMatchRequest request,
                                                     @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return ResponseEntity.ok(matchService.updateMatch(id, request, actor));
    }

    @PutMapping("/{id}/tracking/label-data")
    public ResponseEntity<MatchResponse> updateTrackingLabelData(@PathVariable UUID id,
                                                                 @Valid @RequestBody UpdateTrackingLabelDataRequest request,
                                                                 @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        return ResponseEntity.ok(matchService.updateTrackingLabelData(id, request, actor));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMatch(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        matchService.deleteMatch(id, actor);
        return ResponseEntity.noContent().build();
    }
}
