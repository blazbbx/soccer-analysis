package com.example.footballanalysis.service;

import java.time.LocalDateTime;
import com.example.footballanalysis.repository.MedicalAdminAccessRepository;
import com.example.footballanalysis.repository.ClubRepository;
import com.example.footballanalysis.repository.AuditEventRepository;
import static org.mockito.ArgumentMatchers.eq;
import com.example.footballanalysis.model.db.MedicalCertificate;
import com.example.footballanalysis.model.db.MedicalSubmitterRole;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.repository.MedicalCertificateRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MedicalCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T01:30:00Z");

    @Mock private PlayerProfileRepository playerProfileRepository;
    @Mock private MedicalCertificateRepository certificateRepository;
    @Mock private MedicalRequirementService medicalRequirement;
    @Mock private MedicalFileStore fileStore;
    @Mock private AuditEventService auditEventService;
    @Mock private AuditEventRepository auditEventRepository;
    @Mock private MedicalAdminAccessRepository adminAccessRepository;
    @Mock private ClubRepository clubRepository;
    @Mock private PlatformTransactionManager transactionManager;

    private MedicalCleanupService service;
    private PlayerProfile profile;

    @BeforeEach
    void setUp() {
        service = service(3);
        profile = new PlayerProfile();
        profile.setId(UUID.randomUUID());
        when(playerProfileRepository.findIdsWithMedicalNotRequiredBefore(any())).thenReturn(List.of(profile.getId()));
        when(certificateRepository.findProfileIdsWithHistoryClosedBefore(any())).thenReturn(List.of());
        when(certificateRepository.findProfileIdsWithPendingBefore(any())).thenReturn(List.of());
        when(playerProfileRepository.findByIdForUpdate(profile.getId())).thenReturn(Optional.of(profile));
        when(certificateRepository.findHistoryByProfileId(profile.getId())).thenReturn(List.of());
        when(certificateRepository.findPendingByProfileId(profile.getId())).thenReturn(List.of());
    }

    @Test
    void afterThirtyDaysWithoutRequirementTheCurrentFileIsRemoved() {
        profile.setMedicalNotRequiredSince(NOW.minus(Duration.ofDays(31)));
        MedicalCertificate current = row(NOW.minus(Duration.ofDays(100)));
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(current));
        when(medicalRequirement.isRequired(profile.getId())).thenReturn(false);

        MedicalCleanupService.Result result = service.run();

        assertThat(result.withdrawn()).isEqualTo(1);
        assertThat(current.getWithdrawnAt()).isEqualTo(NOW);
        assertThat(current.getObjectKey()).isNull();
        // Cleared, so the next run does not pick the profile up again.
        assertThat(profile.getMedicalNotRequiredSince()).isNull();
    }

    @Test
    void afterThirtyDaysWithoutRequirementARowWithoutAFileIsWithdrawnToo() {
        profile.setMedicalNotRequiredSince(NOW.minus(Duration.ofDays(31)));
        MedicalCertificate current = row(NOW.minus(Duration.ofDays(100)));
        current.setObjectKey(null);
        current.setValidUntil(null);
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(current));
        when(medicalRequirement.isRequired(profile.getId())).thenReturn(false);

        MedicalCleanupService.Result result = service.run();

        assertThat(result.withdrawn()).isEqualTo(1);
        assertThat(current.getWithdrawnAt()).isEqualTo(NOW);
        assertThat(profile.getMedicalNotRequiredSince()).isNull();
    }

    @Test
    void historyIsLookedUpOnlyForRowsClosedMoreThanAYearAgo() {
        service.run();

        verify(certificateRepository).findProfileIdsWithHistoryClosedBefore(Instant.parse("2025-10-22T01:30:00Z"));
    }

    @Test
    void withinTheGraceOrWhenRequiredAgainTheFileStays() {
        profile.setMedicalNotRequiredSince(NOW.minus(Duration.ofDays(29)));
        MedicalCertificate current = row(NOW.minus(Duration.ofDays(100)));
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(current));

        assertThat(service.run().withdrawn()).isZero();

        profile.setMedicalNotRequiredSince(NOW.minus(Duration.ofDays(31)));
        when(medicalRequirement.isRequired(profile.getId())).thenReturn(true);
        assertThat(service.run().withdrawn()).isZero();
        assertThat(current.getWithdrawnAt()).isNull();
    }

    @Test
    void historyOlderThanTheProfilesRetentionIsDeletedAndNewerHistoryStays() {
        profile.setMedicalRetentionYears(1);
        MedicalCertificate old = row(NOW.minus(Duration.ofDays(800)));
        old.setSupersededAt(NOW.minus(Duration.ofDays(400)));
        MedicalCertificate recent = row(NOW.minus(Duration.ofDays(300)));
        recent.setWithdrawnAt(NOW.minus(Duration.ofDays(200)));
        when(certificateRepository.findHistoryByProfileId(profile.getId())).thenReturn(List.of(recent, old));

        MedicalCleanupService.Result result = service.run();

        assertThat(result.historyDeleted()).isEqualTo(1);
        verify(certificateRepository).delete(old);
        verify(certificateRepository, never()).delete(recent);
    }

    @Test
    void withoutAStoredRetentionTheDefaultThreeYearsApply() {
        MedicalCertificate twoYears = row(NOW.minus(Duration.ofDays(900)));
        twoYears.setSupersededAt(NOW.minus(Duration.ofDays(730)));
        when(certificateRepository.findHistoryByProfileId(profile.getId())).thenReturn(List.of(twoYears));

        assertThat(service.run().historyDeleted()).isZero();
    }

    @Test
    void unfinishedUploadsOlderThanTwentyMinutesAreDeletedEvenWithoutANewSubmission() {
        MedicalCertificate stale = row(NOW.minus(Duration.ofMinutes(21)));
        stale.setCompletedAt(null);
        MedicalCertificate fresh = row(NOW.minus(Duration.ofMinutes(5)));
        fresh.setCompletedAt(null);
        when(certificateRepository.findPendingByProfileId(profile.getId())).thenReturn(List.of(stale, fresh));

        assertThat(service.run().pendingDeleted()).isEqualTo(1);
        verify(certificateRepository).delete(stale);
        verify(certificateRepository, never()).delete(fresh);
    }

    @Test
    void thirtyDaysAfterTheFileWasLastAskedForOnlyTheFileGoesAndTheRowStays() {
        profile.setMedicalFileNotRequiredSince(NOW.minus(Duration.ofDays(31)));
        when(playerProfileRepository.findIdsWithMedicalFileNotRequiredBefore(any())).thenReturn(List.of(profile.getId()));
        MedicalCertificate current = row(NOW.minus(Duration.ofDays(100)));
        String key = current.getObjectKey();
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(current));
        when(medicalRequirement.isRequired(profile.getId())).thenReturn(true);
        when(medicalRequirement.fileRequired(profile.getId())).thenReturn(false);

        MedicalCleanupService.Result result = service.run();

        assertThat(result.filesRemoved()).isEqualTo(1);
        assertThat(result.withdrawn()).isZero();
        assertThat(current.getObjectKey()).isNull();
        assertThat(current.getWithdrawnAt()).isNull();
        assertThat(profile.getMedicalFileNotRequiredSince()).isNull();
        verify(fileStore).deleteAfterCommit(List.of(new MedicalFileStore.KeyRef(key, current.getId())));
        verify(auditEventService).record(eq("MEDICAL_FILE_REMOVED_BY_POLICY"), any(), eq("SYSTEM"), eq("MEDICAL_CERTIFICATE"),
                eq(current.getId().toString()), eq("reason=file_not_required_30_days"));
    }

    @Test
    void onTheTwentyNinthDayOrWhenTheFileIsAskedForAgainItStays() {
        profile.setMedicalFileNotRequiredSince(NOW.minus(Duration.ofDays(29)));
        when(playerProfileRepository.findIdsWithMedicalFileNotRequiredBefore(any())).thenReturn(List.of(profile.getId()));
        MedicalCertificate current = row(NOW.minus(Duration.ofDays(100)));
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(current));
        when(medicalRequirement.isRequired(profile.getId())).thenReturn(true);

        assertThat(service.run().filesRemoved()).isZero();
        assertThat(current.getObjectKey()).isNotNull();

        profile.setMedicalFileNotRequiredSince(NOW.minus(Duration.ofDays(31)));
        when(medicalRequirement.fileRequired(profile.getId())).thenReturn(true);
        assertThat(service.run().filesRemoved()).isZero();
        assertThat(current.getObjectKey()).isNotNull();
        assertThat(profile.getMedicalFileNotRequiredSince()).isNull();
    }

    @Test
    void medicalAuditRowsAndBreakGlassRowsGoAfterTheConfiguredRetention() {
        when(auditEventRepository.deleteMedicalBefore(any())).thenReturn(4);
        when(adminAccessRepository.deleteCreatedBefore(any())).thenReturn(2);

        MedicalCleanupService.Result result = service.run();

        Instant cutoff = Instant.parse("2023-10-22T01:30:00Z");
        verify(auditEventRepository).deleteMedicalBefore(LocalDateTime.ofInstant(cutoff, ZoneId.systemDefault()));
        verify(adminAccessRepository).deleteCreatedBefore(cutoff);
        assertThat(result.auditDeleted()).isEqualTo(4);
        assertThat(result.adminAccessesDeleted()).isEqualTo(2);
    }

    @Test
    void aClubKeepingHistoryForFiveYearsKeepsTheAuditRowsAsLong() {
        when(clubRepository.findMaxMedicalHistoryRetentionYears()).thenReturn(5);

        service.run();

        Instant cutoff = Instant.parse("2021-10-22T01:30:00Z");
        verify(auditEventRepository).deleteMedicalBefore(LocalDateTime.ofInstant(cutoff, ZoneId.systemDefault()));
        verify(adminAccessRepository).deleteCreatedBefore(cutoff);
        // A longer configured value wins over a shorter club value.
        when(clubRepository.findMaxMedicalHistoryRetentionYears()).thenReturn(1);
        assertThat(service(4).effectiveAuditRetentionYears()).isEqualTo(4);
    }

    private MedicalCleanupService service(int auditRetentionYears) {
        return new MedicalCleanupService(playerProfileRepository, certificateRepository, medicalRequirement, fileStore,
                auditEventService, auditEventRepository, adminAccessRepository, clubRepository, auditRetentionYears,
                Clock.fixed(NOW, ZoneId.of("Europe/Budapest")), transactionManager);
    }

    private MedicalCertificate row(Instant createdAt) {
        MedicalCertificate row = new MedicalCertificate();
        row.setId(UUID.randomUUID());
        row.setPlayerProfileId(profile.getId());
        row.setObjectKey("medical/" + profile.getId() + "/" + row.getId());
        row.setValidUntil(LocalDate.of(2026, 5, 3));
        row.setSubmittedByRole(MedicalSubmitterRole.PARENT);
        row.setCreatedAt(createdAt);
        row.setCompletedAt(createdAt.plusSeconds(60));
        return row;
    }
}
