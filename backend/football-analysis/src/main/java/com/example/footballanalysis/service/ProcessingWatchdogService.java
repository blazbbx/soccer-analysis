package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.repository.ClipRepository;
import com.example.footballanalysis.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ends matches and clip renders that a worker will never finish. A worker acknowledges its message before
 * the work, so if it dies mid-way no result ever comes: without this the match stays PROCESSING (or the clip
 * QUEUED) forever, and the user cannot even retry. After the time limit the match goes to ERROR and the clip
 * to FAILED, which the UI already shows and lets the user delete or re-render.
 *
 * <p>Each row is changed in its own transaction under a row lock, and only if it is still in the same state
 * since before the limit, so a result that arrives at the same moment wins.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessingWatchdogService {

    static final Set<String> STUCK_RENDER_STATUSES = Set.of("QUEUED", "PROCESSING");

    private final MatchRepository matchRepository;
    private final ClipRepository clipRepository;
    private final SseNotificationService sseService;

    @Transactional(readOnly = true)
    public List<UUID> findStuckMatches(String status, LocalDateTime before) {
        return matchRepository.findIdsInStatusSince(status, before);
    }

    @Transactional(readOnly = true)
    public List<UUID> findStuckRenders(LocalDateTime before) {
        return clipRepository.findRenderIdsRequestedBefore(STUCK_RENDER_STATUSES, before);
    }

    /** @return true when this call moved the match to ERROR */
    @Transactional
    public boolean failStuckMatch(UUID matchId, String expectedStatus, LocalDateTime before) {
        Match match = matchRepository.findByIdForUpdate(matchId).orElse(null);
        if (match == null
                || !expectedStatus.equals(match.getOverallStatus())
                || match.getStatusChangedAt() == null
                || !match.getStatusChangedAt().isBefore(before)) {
            return false;
        }
        match.setMlStatus(failIfUnfinished(match.getMlStatus()));
        match.setEncodingStatus(failIfUnfinished(match.getEncodingStatus()));
        match.setFieldDetectionStatus(failIfUnfinished(match.getFieldDetectionStatus()));
        match.setOverallStatus("ERROR");
        matchRepository.save(match);
        sseService.notifyClientAfterCommit(matchId.toString(), "ERROR");
        log.warn("Match {} was {} since {}; no worker result came, marked ERROR",
                matchId, expectedStatus, match.getStatusChangedAt());
        return true;
    }

    /** @return true when this call moved the render to FAILED */
    @Transactional
    public boolean failStuckRender(UUID clipId, LocalDateTime before) {
        Clip clip = clipRepository.findByIdForUpdate(clipId).orElse(null);
        if (clip == null
                || !STUCK_RENDER_STATUSES.contains(clip.getRenderStatus())
                || clip.getRenderRequestedAt() == null
                || !clip.getRenderRequestedAt().isBefore(before)) {
            return false;
        }
        clip.setRenderStatus(ClipService.RENDER_STATUS_FAILED);
        clip.setRenderError("The render did not finish in time. Start it again.");
        clipRepository.save(clip);
        log.warn("Clip {} render requested at {} never finished, marked FAILED", clipId, clip.getRenderRequestedAt());
        return true;
    }

    // The worker statuses are PENDING / COMPLETED / FAILED; what has not completed by now never will.
    private static String failIfUnfinished(String status) {
        return "COMPLETED".equals(status) ? status : "FAILED";
    }
}
