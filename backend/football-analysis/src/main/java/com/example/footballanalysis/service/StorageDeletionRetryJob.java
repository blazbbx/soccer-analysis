package com.example.footballanalysis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retries the match and clip file deletions that failed after commit (see {@link MinioObjectCleanupService}). */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.storage-deletion-retry.enabled", havingValue = "true", matchIfMissing = true)
public class StorageDeletionRetryJob {

    private final MinioObjectCleanupService cleanupService;

    @Scheduled(fixedDelayString = "${app.storage-deletion-retry.interval-ms:3600000}",
            initialDelayString = "${app.storage-deletion-retry.initial-delay-ms:300000}")
    public void run() {
        try {
            int done = cleanupService.retryQueuedDeletions();
            if (done > 0) {
                log.info("Finished {} queued MinIO deletions", done);
            }
        } catch (RuntimeException ex) {
            log.error("Retrying the queued MinIO deletions failed; the next run tries again", ex);
        }
    }
}
