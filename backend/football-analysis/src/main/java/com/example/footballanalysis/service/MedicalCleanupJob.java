package com.example.footballanalysis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Checks the storage, then runs the sport medical cleanup every night at 03:30 in the application zone. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.medical.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class MedicalCleanupJob {

    private final MedicalStorageGuard medicalStorageGuard;
    private final MedicalCleanupService medicalCleanupService;

    @Scheduled(cron = "${app.medical.cleanup.cron:0 30 3 * * *}", zone = "${app.time-zone:Europe/Budapest}")
    public void runNightly() {
        medicalStorageGuard.check();
        medicalCleanupService.run();
    }
}
