package com.example.footballanalysis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Every few minutes, fails the matches and clip renders that have been waiting for a worker longer than the
 * limits below (see {@link ProcessingWatchdogService}). AWAITING_CORNERS is not checked: there the user acts.
 *
 * <p>The stored times come from {@code LocalDateTime.now()} (JVM zone), so "now" is read the same way here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.processing.watchdog.enabled", havingValue = "true", matchIfMissing = true)
public class ProcessingWatchdog {

    private final ProcessingWatchdogService watchdogService;

    // The browser upload never finished (closed tab, lost network): the webhook never came.
    @Value("${app.processing.watchdog.uploading-hours:24}")
    private long uploadingHours;

    // Field detection takes minutes.
    @Value("${app.processing.watchdog.preprocessing-hours:2}")
    private long preprocessingHours;

    // Tracking and HLS encoding of a full match; long, but not this long.
    @Value("${app.processing.watchdog.processing-hours:12}")
    private long processingHours;

    @Value("${app.processing.watchdog.render-hours:2}")
    private long renderHours;

    @Scheduled(fixedDelayString = "${app.processing.watchdog.interval-ms:600000}",
            initialDelayString = "${app.processing.watchdog.initial-delay-ms:600000}")
    public void run() {
        LocalDateTime now = LocalDateTime.now();
        int failed = failMatches("UPLOADING", now.minus(Duration.ofHours(uploadingHours)))
                + failMatches("PREPROCESSING", now.minus(Duration.ofHours(preprocessingHours)))
                + failMatches("PROCESSING", now.minus(Duration.ofHours(processingHours)))
                + failRenders(now.minus(Duration.ofHours(renderHours)));
        if (failed > 0) {
            log.warn("Processing watchdog marked {} stuck match(es) or render(s) as failed", failed);
        }
    }

    private int failMatches(String status, LocalDateTime before) {
        int count = 0;
        for (UUID id : watchdogService.findStuckMatches(status, before)) {
            try {
                if (watchdogService.failStuckMatch(id, status, before)) {
                    count++;
                }
            } catch (RuntimeException ex) {
                // One bad row must not stop the others; the next run tries it again.
                log.error("Processing watchdog could not fail match {}", id, ex);
            }
        }
        return count;
    }

    private int failRenders(LocalDateTime before) {
        int count = 0;
        for (UUID id : watchdogService.findStuckRenders(before)) {
            try {
                if (watchdogService.failStuckRender(id, before)) {
                    count++;
                }
            } catch (RuntimeException ex) {
                log.error("Processing watchdog could not fail clip render {}", id, ex);
            }
        }
        return count;
    }
}
