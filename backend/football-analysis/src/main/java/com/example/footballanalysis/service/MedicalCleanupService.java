package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.MedicalCertificate;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.repository.AuditEventRepository;
import com.example.footballanalysis.repository.ClubRepository;
import com.example.footballanalysis.repository.MedicalAdminAccessRepository;
import com.example.footballanalysis.repository.MedicalCertificateRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The daily sport medical cleanup. Each profile runs in its own transaction under the profile lock, so a
 * second run does the same thing and a failure on one profile does not stop the others.
 * <ol>
 *     <li>A profile that has not needed a certificate for 30 days loses its current row, with or without a file, and
 *     its grace clock is cleared so the next run does not pick it up again.</li>
 *     <li>History rows older than the profile's retention are deleted. The current certificate is never history.</li>
 *     <li>Pending uploads older than 20 minutes are deleted, even when no new submission comes for that profile.</li>
 *     <li>A child still required but no longer asked for a file for 30 days loses only the file; the row with its
 *     result and date stays current (docs/sportorvosi-jogszeruseg.md, Módváltás).</li>
 *     <li>Sport medical audit rows ({@code MEDICAL_} event types) and break-glass rows older than the effective audit
 *     retention are deleted in one batch each (docs/sportorvosi-jogszeruseg.md, Megőrzés és takarítás). The effective
 *     retention is the larger of {@code app.medical.audit-retention-years} and the longest club history retention, so a
 *     history row never outlives the audit rows that tell who submitted, opened or withdrew it.</li>
 *     <li>File deletions that failed earlier are tried again.</li>
 * </ol>
 */
@Service
@Slf4j
public class MedicalCleanupService {

    static final Duration GRACE = Duration.ofDays(30);
    static final int MIN_RETENTION_YEARS = 1;

    private final PlayerProfileRepository playerProfileRepository;
    private final MedicalCertificateRepository certificateRepository;
    private final MedicalRequirementService medicalRequirement;
    private final MedicalFileStore fileStore;
    private final AuditEventService auditEventService;
    private final AuditEventRepository auditEventRepository;
    private final MedicalAdminAccessRepository adminAccessRepository;
    private final ClubRepository clubRepository;
    private final int auditRetentionYears;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public MedicalCleanupService(PlayerProfileRepository playerProfileRepository,
                                 MedicalCertificateRepository certificateRepository,
                                 MedicalRequirementService medicalRequirement,
                                 MedicalFileStore fileStore,
                                 AuditEventService auditEventService,
                                 AuditEventRepository auditEventRepository,
                                 MedicalAdminAccessRepository adminAccessRepository,
                                 ClubRepository clubRepository,
                                 @Value("${app.medical.audit-retention-years:3}") int auditRetentionYears,
                                 Clock clock,
                                 PlatformTransactionManager transactionManager) {
        this.playerProfileRepository = playerProfileRepository;
        this.certificateRepository = certificateRepository;
        this.medicalRequirement = medicalRequirement;
        this.fileStore = fileStore;
        this.auditEventService = auditEventService;
        this.auditEventRepository = auditEventRepository;
        this.adminAccessRepository = adminAccessRepository;
        this.clubRepository = clubRepository;
        this.auditRetentionYears = auditRetentionYears;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public Result run() {
        Instant now = clock.instant();
        Set<UUID> profileIds = new LinkedHashSet<>();
        profileIds.addAll(playerProfileRepository.findIdsWithMedicalNotRequiredBefore(now.minus(GRACE)));
        // No retention is shorter than a year, so a row closed later than that cannot be due yet.
        Instant shortestCutoff = ZonedDateTime.ofInstant(now, clock.getZone()).minusYears(MIN_RETENTION_YEARS).toInstant();
        profileIds.addAll(certificateRepository.findProfileIdsWithHistoryClosedBefore(shortestCutoff));
        profileIds.addAll(certificateRepository.findProfileIdsWithPendingBefore(now.minus(MedicalCertificateService.PENDING_TTL)));
        profileIds.addAll(playerProfileRepository.findIdsWithMedicalFileNotRequiredBefore(now.minus(GRACE)));

        Result total = new Result(0, 0, 0, 0, 0, 0);
        for (UUID profileId : profileIds) {
            try {
                Result one = transaction.execute(status -> cleanProfile(profileId, now));
                if (one != null) {
                    total = total.plus(one);
                }
            } catch (RuntimeException ex) {
                log.warn("Medical cleanup failed for profile {}", profileId, ex);
            }
        }
        try {
            Result audit = transaction.execute(status -> deleteOldAuditRows(now));
            if (audit != null) {
                total = total.plus(audit);
            }
        } catch (RuntimeException ex) {
            log.warn("Medical audit cleanup failed", ex);
        }
        // Last, so a delete that failed during this run gets its first retry at once.
        try {
            fileStore.retryQueuedDeletions();
        } catch (RuntimeException ex) {
            log.warn("Medical file deletion retry failed", ex);
        }
        log.info("Medical cleanup: {} certificates withdrawn after the grace period, {} files no longer asked for removed, "
                        + "{} history rows and {} unfinished uploads deleted, {} audit rows and {} break-glass rows deleted",
                total.withdrawn(), total.filesRemoved(), total.historyDeleted(), total.pendingDeleted(),
                total.auditDeleted(), total.adminAccessesDeleted());
        return total;
    }

    /** The larger of the configured audit retention and the longest club history retention, read once per run. */
    int effectiveAuditRetentionYears() {
        Integer longestHistory = clubRepository.findMaxMedicalHistoryRetentionYears();
        return Math.max(auditRetentionYears, longestHistory != null ? longestHistory : 0);
    }

    private Result deleteOldAuditRows(Instant now) {
        int years = effectiveAuditRetentionYears();
        Instant cutoff = ZonedDateTime.ofInstant(now, clock.getZone()).minusYears(years).toInstant();
        // audit_events.created_at is the JVM's local time without a zone (AuditEvent#prePersist), so the cutoff is too.
        int audit = auditEventRepository.deleteMedicalBefore(LocalDateTime.ofInstant(cutoff, ZoneId.systemDefault()));
        int accesses = adminAccessRepository.deleteCreatedBefore(cutoff);
        return new Result(0, 0, 0, 0, audit, accesses);
    }

    private Result cleanProfile(UUID profileId, Instant now) {
        PlayerProfile profile = playerProfileRepository.findByIdForUpdate(profileId).orElse(null);
        if (profile == null) {
            return new Result(0, 0, 0, 0, 0, 0);
        }
        List<MedicalFileStore.KeyRef> keys = new ArrayList<>();
        int withdrawn = 0;
        Instant notRequiredSince = profile.getMedicalNotRequiredSince();
        if (notRequiredSince != null && notRequiredSince.isBefore(now.minus(GRACE))
                && !medicalRequirement.isRequired(profileId)) {
            MedicalCertificate current = certificateRepository.findCurrentByProfileId(profileId).orElse(null);
            if (current != null) {
                keys.add(new MedicalFileStore.KeyRef(current.getObjectKey(), current.getId()));
                current.setWithdrawnAt(now);
                current.setObjectKey(null);
                certificateRepository.save(current);
                auditEventService.record(
                        "MEDICAL_CERTIFICATE_REMOVED_BY_POLICY",
                        null,
                        "SYSTEM",
                        "MEDICAL_CERTIFICATE",
                        current.getId().toString(),
                        "reason=not_required_30_days");
                withdrawn++;
            }
            profile.setMedicalNotRequiredSince(null);
        }

        int filesRemoved = 0;
        Instant fileNotRequiredSince = profile.getMedicalFileNotRequiredSince();
        if (fileNotRequiredSince != null && fileNotRequiredSince.isBefore(now.minus(GRACE))) {
            if (medicalRequirement.isRequired(profileId) && !medicalRequirement.fileRequired(profileId)) {
                MedicalCertificate current = certificateRepository.findCurrentByProfileId(profileId).orElse(null);
                if (current != null && current.hasFile()) {
                    keys.add(new MedicalFileStore.KeyRef(current.getObjectKey(), current.getId()));
                    current.setObjectKey(null);
                    certificateRepository.save(current);
                    auditEventService.record(
                            "MEDICAL_FILE_REMOVED_BY_POLICY",
                            null,
                            "SYSTEM",
                            "MEDICAL_CERTIFICATE",
                            current.getId().toString(),
                            "reason=file_not_required_30_days");
                    filesRemoved++;
                }
            }
            profile.setMedicalFileNotRequiredSince(null);
        }

        int years = profile.getMedicalRetentionYears() != null
                ? profile.getMedicalRetentionYears()
                : MedicalRequirementService.DEFAULT_RETENTION_YEARS;
        Instant historyCutoff = ZonedDateTime.ofInstant(now, clock.getZone()).minusYears(years).toInstant();
        int historyDeleted = 0;
        for (MedicalCertificate row : certificateRepository.findHistoryByProfileId(profileId)) {
            Instant closedAt = row.getWithdrawnAt() != null ? row.getWithdrawnAt() : row.getSupersededAt();
            if (closedAt != null && closedAt.isBefore(historyCutoff)) {
                keys.add(new MedicalFileStore.KeyRef(row.getObjectKey(), row.getId()));
                certificateRepository.delete(row);
                historyDeleted++;
            }
        }

        int pendingDeleted = 0;
        for (MedicalCertificate pending : certificateRepository.findPendingByProfileId(profileId)) {
            if (pending.getCreatedAt().isBefore(now.minus(MedicalCertificateService.PENDING_TTL))) {
                keys.add(new MedicalFileStore.KeyRef(pending.getObjectKey(), pending.getId()));
                certificateRepository.delete(pending);
                pendingDeleted++;
            }
        }
        fileStore.deleteAfterCommit(keys);
        return new Result(withdrawn, historyDeleted, pendingDeleted, filesRemoved, 0, 0);
    }

    public record Result(int withdrawn, int historyDeleted, int pendingDeleted, int filesRemoved,
                         int auditDeleted, int adminAccessesDeleted) {
        Result plus(Result other) {
            return new Result(withdrawn + other.withdrawn, historyDeleted + other.historyDeleted,
                    pendingDeleted + other.pendingDeleted, filesRemoved + other.filesRemoved,
                    auditDeleted + other.auditDeleted, adminAccessesDeleted + other.adminAccessesDeleted);
        }
    }
}
