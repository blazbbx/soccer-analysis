package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.repository.ClipRepository;
import com.example.footballanalysis.repository.MatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessingWatchdogServiceTest {

    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 11, 16, 10, 0);

    @Mock private MatchRepository matchRepository;
    @Mock private ClipRepository clipRepository;
    @Mock private SseNotificationService sseService;

    private ProcessingWatchdogService service;

    @BeforeEach
    void setUp() {
        service = new ProcessingWatchdogService(matchRepository, clipRepository, sseService);
    }

    @Test
    void failStuckMatch_marksAProcessingMatchErrorAndKeepsFinishedWorkers() {
        Match match = match("PROCESSING", CUTOFF.minusHours(1));
        match.setMlStatus("PENDING");
        match.setEncodingStatus("COMPLETED");
        match.setFieldDetectionStatus("COMPLETED");
        when(matchRepository.findByIdForUpdate(match.getId())).thenReturn(Optional.of(match));

        assertThat(service.failStuckMatch(match.getId(), "PROCESSING", CUTOFF)).isTrue();

        assertThat(match.getOverallStatus()).isEqualTo("ERROR");
        assertThat(match.getMlStatus()).isEqualTo("FAILED");
        assertThat(match.getEncodingStatus()).isEqualTo("COMPLETED");
        assertThat(match.getFieldDetectionStatus()).isEqualTo("COMPLETED");
        verify(matchRepository).save(match);
        verify(sseService).notifyClientAfterCommit(match.getId().toString(), "ERROR");
    }

    @Test
    void failStuckMatch_leavesAMatchThatMovedOnMeanwhile() {
        Match match = match("COMPLETED", CUTOFF.minusHours(1));
        when(matchRepository.findByIdForUpdate(match.getId())).thenReturn(Optional.of(match));

        assertThat(service.failStuckMatch(match.getId(), "PROCESSING", CUTOFF)).isFalse();

        assertThat(match.getOverallStatus()).isEqualTo("COMPLETED");
        verify(matchRepository, never()).save(any());
        verify(sseService, never()).notifyClientAfterCommit(anyString(), anyString());
    }

    @Test
    void failStuckMatch_leavesAMatchThatChangedStatusAfterTheCutoff() {
        Match match = match("PROCESSING", CUTOFF.plusMinutes(5));
        when(matchRepository.findByIdForUpdate(match.getId())).thenReturn(Optional.of(match));

        assertThat(service.failStuckMatch(match.getId(), "PROCESSING", CUTOFF)).isFalse();
        verify(matchRepository, never()).save(any());
    }

    @Test
    void failStuckRender_failsAQueuedRenderPastTheCutoff() {
        Clip clip = clip("QUEUED", CUTOFF.minusHours(1));
        when(clipRepository.findByIdForUpdate(clip.getId())).thenReturn(Optional.of(clip));

        assertThat(service.failStuckRender(clip.getId(), CUTOFF)).isTrue();

        assertThat(clip.getRenderStatus()).isEqualTo("FAILED");
        assertThat(clip.getRenderError()).isNotBlank();
        verify(clipRepository).save(clip);
    }

    @Test
    void failStuckRender_leavesACompletedRender() {
        Clip clip = clip("COMPLETED", CUTOFF.minusHours(1));
        when(clipRepository.findByIdForUpdate(clip.getId())).thenReturn(Optional.of(clip));

        assertThat(service.failStuckRender(clip.getId(), CUTOFF)).isFalse();
        verify(clipRepository, never()).save(any());
    }

    @Test
    void setOverallStatus_stampsTheTimeOnlyOnARealChange() {
        Match match = new Match();
        match.setOverallStatus("PROCESSING");
        LocalDateTime stamped = match.getStatusChangedAt();
        assertThat(stamped).isNotNull();

        match.setStatusChangedAt(CUTOFF);
        match.setOverallStatus("PROCESSING");
        assertThat(match.getStatusChangedAt()).isEqualTo(CUTOFF);

        match.setOverallStatus("COMPLETED");
        assertThat(match.getStatusChangedAt()).isAfter(CUTOFF);
    }

    private static Match match(String status, LocalDateTime changedAt) {
        Match match = new Match();
        match.setId(UUID.randomUUID());
        match.setOverallStatus(status);
        match.setStatusChangedAt(changedAt);
        return match;
    }

    private static Clip clip(String status, LocalDateTime requestedAt) {
        Clip clip = new Clip();
        clip.setId(UUID.randomUUID());
        clip.setRenderStatus(status);
        clip.setRenderRequestedAt(requestedAt);
        return clip;
    }
}
