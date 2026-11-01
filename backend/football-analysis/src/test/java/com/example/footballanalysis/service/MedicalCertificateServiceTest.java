package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.AppException;
import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.MedicalAdminAccess;
import com.example.footballanalysis.model.db.MedicalCertificate;
import com.example.footballanalysis.model.db.MedicalResult;
import com.example.footballanalysis.model.db.MedicalSubmitterRole;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.user.Admin;
import com.example.footballanalysis.model.db.user.ClubAdmin;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.MedicalAdminAccessRequest;
import com.example.footballanalysis.model.requests.MedicalAdminWithdrawalRequest;
import com.example.footballanalysis.model.requests.SetMedicalUploadRightRequest;
import com.example.footballanalysis.model.requests.SubmitMedicalCertificateRequest;
import com.example.footballanalysis.model.responses.MedicalAdminAccessItem;
import com.example.footballanalysis.model.responses.MedicalAdminAccessResponse;
import com.example.footballanalysis.model.responses.MedicalNoticeItem;
import com.example.footballanalysis.model.responses.MedicalStatusResponse;
import com.example.footballanalysis.model.responses.PageResponse;
import com.example.footballanalysis.exception.ExternalServiceException;
import com.example.footballanalysis.repository.AuditEventRepository;
import com.example.footballanalysis.repository.MedicalAdminAccessRepository;
import com.example.footballanalysis.repository.MedicalCertificateRepository;
import com.example.footballanalysis.repository.MedicalParentSeenRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.TeamRosterRepository;
import com.example.footballanalysis.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MedicalCertificateServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    private static final ZoneId BUDAPEST = ZoneId.of("Europe/Budapest");
    private static final String REASON = "The club asked for a check of the expiry date.";

    @Mock private MedicalCertificateRepository certificateRepository;
    @Mock private MedicalAdminAccessRepository adminAccessRepository;
    @Mock private PlayerProfileRepository playerProfileRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private TeamRosterRepository teamRosterRepository;
    @Mock private TeamMembershipQuery teamMembershipQuery;
    @Mock private UserRepository userRepository;
    @Mock private UserAccessService userAccessService;
    @Mock private MedicalAccessService medicalAccess;
    @Mock private MedicalRequirementService medicalRequirement;
    @Mock private MedicalFileStore fileStore;
    @Mock private MedicalStorageGuard storageGuard;
    @Mock private MedicalCompliance compliance;
    @Mock private AuditEventService auditEventService;
    @Mock private AuditEventRepository auditEventRepository;
    @Mock private MedicalParentSeenRepository parentSeenRepository;
    @Mock private PlatformTransactionManager transactionManager;

    private MedicalCertificateService service;
    private PlayerProfile profile;
    private Parent parent;

    @BeforeEach
    void setUp() {
        service = new MedicalCertificateService(certificateRepository, adminAccessRepository, playerProfileRepository, teamRepository,
                teamRosterRepository, teamMembershipQuery, userRepository, userAccessService, medicalAccess,
                medicalRequirement, fileStore, storageGuard, compliance, auditEventService, auditEventRepository, parentSeenRepository, Clock.fixed(NOW, BUDAPEST), transactionManager);
        profile = new PlayerProfile();
        profile.setId(UUID.randomUUID());
        parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setRole(UserRole.PARENT);
        when(playerProfileRepository.findById(profile.getId())).thenReturn(Optional.of(profile));
        when(playerProfileRepository.findByIdForUpdate(profile.getId())).thenReturn(Optional.of(profile));
        when(medicalAccess.submitterRole(parent, profile)).thenReturn(Optional.of(MedicalSubmitterRole.PARENT));
        when(medicalAccess.managerRole(parent, profile.getId())).thenReturn(Optional.of(MedicalSubmitterRole.PARENT));
        when(medicalAccess.isParent(parent, profile.getId())).thenReturn(true);
        when(medicalAccess.isParentOfRecord(parent, profile.getId())).thenReturn(true);
        // Children are minors unless a test says otherwise.
        when(medicalAccess.isMinor(any())).thenReturn(true);
        when(medicalRequirement.isRequired(profile.getId())).thenReturn(true);
        // The tests below the PR 10 section exercise the file mode: one ON team asks for the redacted copy.
        when(medicalRequirement.fileRequired(profile.getId())).thenReturn(true);
        when(certificateRepository.findPendingByProfileId(profile.getId())).thenReturn(List.of());
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.empty());
        when(certificateRepository.findHistoryByProfileId(profile.getId())).thenReturn(List.of());
        when(fileStore.uploadUrl(anyString(), anyString(), anyLong())).thenReturn("http://minio/put");
    }

    // ---------------------------------------------------------------- submit

    @Test
    void submitIsRefusedWhenTheClubDoesNotAskForIt() {
        when(medicalRequirement.isRequired(profile.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.submit(profile.getId(), request(LocalDate.of(2027, 5, 3), "image/jpeg", 1000), parent))
                .isInstanceOf(ForbiddenException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getMessageCode()).isEqualTo("error.medical.not_required"));
        verify(certificateRepository, never()).save(any());
    }

    @Test
    void submitValidatesEveryFieldWithItsOwnKeyAndSavesNothing() {
        assertCode(() -> service.submit(profile.getId(), new SubmitMedicalCertificateRequest("VERSENYEZHET", LocalDate.of(2027, 5, 3), false, "image/jpeg", 10L, true), parent),
                "error.medical.acknowledgement_required");
        assertCode(() -> service.submit(profile.getId(), request(null, "image/jpeg", 10), parent),
                "validation.medical.valid_until");
        assertCode(() -> service.submit(profile.getId(), request(LocalDate.of(2027, 5, 3), "image/gif", 10), parent),
                "error.medical.file_type");
        assertCode(() -> service.submit(profile.getId(), request(LocalDate.of(2027, 5, 3), "image/jpeg", 0), parent),
                "error.medical.file_too_large");
        assertCode(() -> service.submit(profile.getId(), request(LocalDate.of(2027, 5, 3), "image/jpeg", 10_485_761), parent),
                "error.medical.file_too_large");
        verify(certificateRepository, never()).save(any());
    }

    @Test
    void anUnsecuredStorageRefusesEverySubmissionWithA503AndSavesNothing() {
        doThrow(new ExternalServiceException("error.medical.storage_not_secure", new Object[0], "not secure"))
                .when(storageGuard).requireSecure();

        assertThatThrownBy(() -> service.submit(profile.getId(), request(LocalDate.of(2027, 5, 3), "image/jpeg", 1000), parent))
                .isInstanceOf(ExternalServiceException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getMessageCode()).isEqualTo("error.medical.storage_not_secure"));
        verify(certificateRepository, never()).save(any());
        verify(fileStore, never()).uploadUrl(anyString(), anyString(), anyLong());
    }

    @Test
    void submitAcceptsAPastDateAndSignsTheDeclaredSize() {
        var response = service.submit(profile.getId(), request(LocalDate.of(2026, 1, 1), "image/jpeg", 10_485_760), parent);

        ArgumentCaptor<MedicalCertificate> saved = ArgumentCaptor.forClass(MedicalCertificate.class);
        verify(certificateRepository).save(saved.capture());
        MedicalCertificate row = saved.getValue();
        assertThat(row.getSubmittedByRole()).isEqualTo(MedicalSubmitterRole.PARENT);
        assertThat(row.getAcknowledgementVersion()).isEqualTo("sport-medical-v2-file");
        assertThat(row.getEvidence()).isEqualTo(MedicalCertificate.Evidence.FILE);
        assertThat(row.getResult()).isEqualTo(MedicalResult.VERSENYEZHET);
        assertThat(row.getAcknowledgedAt()).isEqualTo(NOW);
        assertThat(row.getCompletedAt()).isNull();
        assertThat(row.getObjectKey()).isEqualTo("medical/" + profile.getId() + "/" + row.getId());
        verify(fileStore).uploadUrl(row.getObjectKey(), "image/jpeg", 10_485_760L);
        assertThat(response.expiresInSeconds()).isEqualTo(600);
    }

    @Test
    void aPastDateCannotReplaceAStillValidCertificate() {
        MedicalCertificate valid = current(LocalDate.of(2027, 5, 3));
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(valid));

        assertCode(() -> service.submit(profile.getId(), request(LocalDate.of(2025, 10, 1), "image/jpeg", 10), parent),
                "validation.medical.valid_until_past_replace");
        verify(certificateRepository, never()).save(any());

        // An expired current one can still be replaced by another past date.
        valid.setValidUntil(LocalDate.of(2026, 1, 1));
        service.submit(profile.getId(), request(LocalDate.of(2025, 10, 1), "image/jpeg", 10), parent);
        verify(certificateRepository).save(any());
    }

    @Test
    void submitDropsStalePendingRowsAndRefusesAFourthOpenUpload() {
        MedicalCertificate stale = pending(NOW.minus(Duration.ofMinutes(21)));
        List<MedicalCertificate> fresh = List.of(pending(NOW.minusSeconds(60)), pending(NOW.minusSeconds(120)), pending(NOW.minusSeconds(180)));
        when(certificateRepository.findPendingByProfileId(profile.getId()))
                .thenReturn(List.of(stale, fresh.get(0), fresh.get(1), fresh.get(2)));

        assertCode(() -> service.submit(profile.getId(), request(LocalDate.of(2027, 5, 3), "image/jpeg", 10), parent),
                "error.medical.too_many_pending");
        verify(certificateRepository).delete(stale);
        verify(fileStore).deleteAfterCommit(List.of(new MedicalFileStore.KeyRef(stale.getObjectKey(), stale.getId())));
    }

    // ---------------------------------------------------------------- complete

    @Test
    void aMismatchingFileDeletesThePendingRowCommitsAndOnlyThenThrows() {
        MedicalCertificate upload = pending(NOW.minusSeconds(30));
        upload.setContentType("image/jpeg");
        upload.setSizeBytes(1000L);
        when(certificateRepository.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(fileStore.size(upload.getObjectKey())).thenReturn(Optional.of(1000L));
        when(fileStore.firstBytes(upload.getObjectKey(), 8)).thenReturn(Optional.of(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}));

        assertCode(() -> service.complete(profile.getId(), upload.getId(), parent), "error.medical.file_mismatch");

        InOrder order = inOrder(certificateRepository, transactionManager, fileStore);
        order.verify(certificateRepository).delete(upload);
        order.verify(transactionManager).commit(any());
        order.verify(fileStore).delete(upload.getObjectKey(), upload.getId());
        verify(certificateRepository, never()).saveAndFlush(any());
    }

    @Test
    void aFileNoLongerAskedForDuringTheUploadIsDeletedAndDoesNotTurnCurrent() {
        MedicalCertificate upload = pending(NOW.minusSeconds(30));
        MedicalCertificate old = current(LocalDate.of(2027, 5, 3));
        when(certificateRepository.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(old));
        when(medicalRequirement.fileRequired(profile.getId())).thenReturn(false);

        assertCode(() -> service.complete(profile.getId(), upload.getId(), parent), "error.medical.file_not_requested");

        InOrder order = inOrder(certificateRepository, transactionManager, fileStore);
        order.verify(certificateRepository).delete(upload);
        order.verify(transactionManager).commit(any());
        order.verify(fileStore).delete(upload.getObjectKey(), upload.getId());
        verify(fileStore, never()).size(anyString());
        assertThat(upload.getCompletedAt()).isNull();
        assertThat(old.getSupersededAt()).isNull();
    }

    @Test
    void aPendingRowPastTheTtlCannotTurnCurrent() {
        MedicalCertificate upload = pending(NOW.minus(Duration.ofMinutes(25)));
        MedicalCertificate old = current(LocalDate.of(2027, 5, 3));
        when(certificateRepository.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(old));

        assertCode(() -> service.complete(profile.getId(), upload.getId(), parent), "error.medical.upload_expired");

        verify(certificateRepository).delete(upload);
        verify(transactionManager).commit(any());
        verify(fileStore).delete(upload.getObjectKey(), upload.getId());
        verify(fileStore, never()).size(anyString());
        assertThat(old.getSupersededAt()).isNull();
    }

    @Test
    void aMissingFileDeletesThePendingRowAndHasNoBytesToDelete() {
        MedicalCertificate upload = pending(NOW.minusSeconds(30));
        when(certificateRepository.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(fileStore.size(upload.getObjectKey())).thenReturn(Optional.empty());

        assertCode(() -> service.complete(profile.getId(), upload.getId(), parent), "error.medical.file_missing");

        verify(certificateRepository).delete(upload);
        verify(transactionManager).commit(any());
        verify(fileStore, never()).delete(any(), any());
        verify(fileStore, never()).firstBytes(anyString(), anyInt());
    }

    @Test
    void aStorageOutageOnTheSizeCheckKeepsThePendingRowAndCanBeRetried() {
        MedicalCertificate upload = pending(NOW.minusSeconds(30));
        when(certificateRepository.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(fileStore.size(upload.getObjectKey()))
                .thenThrow(new ExternalServiceException("error.medical.storage_unavailable", new Object[0], "down"));

        assertThatThrownBy(() -> service.complete(profile.getId(), upload.getId(), parent))
                .isInstanceOf(ExternalServiceException.class);

        verify(certificateRepository, never()).delete(any(MedicalCertificate.class));
        verify(transactionManager, never()).commit(any());
        verify(transactionManager).rollback(any());
        verify(fileStore, never()).delete(any(), any());
    }

    @Test
    void aStorageOutageOnTheFirstBytesKeepsTheRowAndTheFile() {
        MedicalCertificate upload = pending(NOW.minusSeconds(30));
        upload.setContentType("image/jpeg");
        upload.setSizeBytes(1000L);
        when(certificateRepository.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(fileStore.size(upload.getObjectKey())).thenReturn(Optional.of(1000L));
        when(fileStore.firstBytes(upload.getObjectKey(), 8))
                .thenThrow(new ExternalServiceException("error.medical.storage_unavailable", new Object[0], "down"));

        assertThatThrownBy(() -> service.complete(profile.getId(), upload.getId(), parent))
                .isInstanceOf(ExternalServiceException.class);

        verify(certificateRepository, never()).delete(any(MedicalCertificate.class));
        verify(transactionManager, never()).commit(any());
        verify(fileStore, never()).delete(any(), any());
    }

    @Test
    void aFileGoneBetweenTheTwoReadsCountsAsMissing() {
        MedicalCertificate upload = pending(NOW.minusSeconds(30));
        upload.setContentType("image/jpeg");
        upload.setSizeBytes(1000L);
        when(certificateRepository.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(fileStore.size(upload.getObjectKey())).thenReturn(Optional.of(1000L));
        when(fileStore.firstBytes(upload.getObjectKey(), 8)).thenReturn(Optional.empty());

        assertCode(() -> service.complete(profile.getId(), upload.getId(), parent), "error.medical.file_missing");

        verify(certificateRepository).delete(upload);
        verify(fileStore, never()).delete(any(), any());
    }

    @Test
    void aMatchingFileSupersedesTheOldCertificateBeforeTheNewOneTurnsCurrent() {
        MedicalCertificate old = current(LocalDate.of(2026, 11, 1));
        String oldKey = old.getObjectKey();
        MedicalCertificate upload = pending(NOW.minusSeconds(30));
        upload.setContentType("image/jpeg");
        upload.setSizeBytes(1000L);
        upload.setValidUntil(LocalDate.of(2027, 11, 1));
        when(certificateRepository.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(old));
        when(fileStore.size(upload.getObjectKey())).thenReturn(Optional.of(1000L));
        when(fileStore.firstBytes(upload.getObjectKey(), 8)).thenReturn(Optional.of(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));

        service.complete(profile.getId(), upload.getId(), parent);

        InOrder order = inOrder(certificateRepository);
        order.verify(certificateRepository).saveAndFlush(old);
        order.verify(certificateRepository).save(upload);
        assertThat(old.getSupersededAt()).isEqualTo(NOW);
        assertThat(old.getObjectKey()).isNull();
        assertThat(upload.getCompletedAt()).isEqualTo(NOW);
        verify(fileStore).deleteAfterCommit(oldKey, old.getId());
        verify(fileStore, never()).delete(any(), any());
        verify(auditEventService).record(eq("MEDICAL_CERTIFICATE_SUBMITTED"), any(), any(), eq("MEDICAL_CERTIFICATE"), any(), any());
    }

    // ---------------------------------------------------------------- complete re-checks the replacement

    private MedicalCertificate uploadReady(LocalDate validUntil) {
        MedicalCertificate upload = pending(NOW.minusSeconds(600));
        upload.setValidUntil(validUntil);
        when(certificateRepository.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(fileStore.size(upload.getObjectKey())).thenReturn(Optional.of(1000L));
        when(fileStore.firstBytes(upload.getObjectKey(), 8)).thenReturn(Optional.of(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));
        return upload;
    }

    private void assertCompleteRefused(MedicalCertificate upload, MedicalCertificate kept, String keptKey, String code) {
        assertCode(() -> service.complete(profile.getId(), upload.getId(), parent), code);
        verify(certificateRepository).delete(upload);
        verify(fileStore).delete(upload.getObjectKey(), upload.getId());
        assertThat(kept.getSupersededAt()).isNull();
        assertThat(kept.getObjectKey()).isEqualTo(keptKey);
        verify(fileStore, never()).deleteAfterCommit(eq(keptKey), any());
    }

    @Test
    void aPositiveUploadCannotOverwriteACertificateRecordedAfterItStarted() {
        MedicalCertificate upload = uploadReady(LocalDate.of(2028, 5, 3));
        MedicalCertificate newer = current(LocalDate.of(2027, 5, 3));
        newer.setCompletedAt(NOW.minusSeconds(60));
        String newerKey = newer.getObjectKey();
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(newer));

        assertCompleteRefused(upload, newer, newerKey, "error.medical.replaced_meanwhile");
    }

    @Test
    void aNegativeUploadStillReplacesACertificateRecordedAfterItStarted() {
        MedicalCertificate upload = uploadReady(LocalDate.of(2027, 1, 1));
        upload.setResult(MedicalResult.IDEIGLENESEN_ELTILTVA);
        MedicalCertificate newer = current(LocalDate.of(2027, 5, 3));
        newer.setCompletedAt(NOW.minusSeconds(60));
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(newer));

        service.complete(profile.getId(), upload.getId(), parent);

        assertThat(newer.getSupersededAt()).isEqualTo(NOW);
        assertThat(upload.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    void aPastDateUploadCannotReplaceAStillValidCertificateAtComplete() {
        MedicalCertificate upload = uploadReady(LocalDate.of(2025, 10, 1));
        MedicalCertificate valid = current(LocalDate.of(2027, 5, 3));
        String validKey = valid.getObjectKey();
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(valid));

        assertCompleteRefused(upload, valid, validKey, "validation.medical.valid_until_past_replace");
    }

    @Test
    void aCoachUploadCannotReplaceAFileThatIsNotDueAtComplete() {
        MedicalCertificate upload = uploadReady(LocalDate.of(2028, 5, 3));
        upload.setSubmittedByRole(MedicalSubmitterRole.COACH);
        MedicalCertificate notDue = current(LocalDate.of(2027, 5, 3));
        String notDueKey = notDue.getObjectKey();
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(notDue));

        assertCompleteRefused(upload, notDue, notDueKey, "error.medical.replace_too_early");
    }

    @Test
    void aCoachUploadReplacesAFileThatIsDueWithALaterDate() {
        MedicalCertificate upload = uploadReady(LocalDate.of(2027, 5, 3));
        upload.setSubmittedByRole(MedicalSubmitterRole.COACH);
        MedicalCertificate due = current(NOW.atZone(BUDAPEST).toLocalDate().plusDays(10));
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(due));

        service.complete(profile.getId(), upload.getId(), parent);

        assertThat(due.getSupersededAt()).isEqualTo(NOW);
        assertThat(upload.getCompletedAt()).isEqualTo(NOW);
    }

    // ---------------------------------------------------------------- result and evidence (PR 10)

    @Test
    void aDateOnlySubmissionIsCurrentAtOnceWithoutAnUploadAndEvenWithAnUnsecuredStorage() {
        when(medicalRequirement.fileRequired(profile.getId())).thenReturn(false);
        doThrow(new ExternalServiceException("error.medical.storage_not_secure", new Object[0], "not secure"))
                .when(storageGuard).requireSecure();
        MedicalCertificate old = current(LocalDate.of(2026, 11, 1));
        String oldKey = old.getObjectKey();
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(old));

        var response = service.submit(profile.getId(), dateOnly("ALKALMAS", LocalDate.of(2027, 5, 3)), parent);

        assertThat(response.completed()).isTrue();
        assertThat(response.uploadUrl()).isNull();
        ArgumentCaptor<MedicalCertificate> saved = ArgumentCaptor.forClass(MedicalCertificate.class);
        verify(certificateRepository).save(saved.capture());
        MedicalCertificate row = saved.getValue();
        assertThat(row.getEvidence()).isEqualTo(MedicalCertificate.Evidence.DATE_ONLY);
        assertThat(row.getResult()).isEqualTo(MedicalResult.ALKALMAS);
        assertThat(row.getCompletedAt()).isEqualTo(NOW);
        assertThat(row.getObjectKey()).isNull();
        assertThat(row.getContentType()).isNull();
        assertThat(row.getAcknowledgementVersion()).isEqualTo("sport-medical-v2-date");
        InOrder order = inOrder(certificateRepository);
        order.verify(certificateRepository).saveAndFlush(old);
        order.verify(certificateRepository).save(row);
        verify(fileStore).deleteAfterCommit(oldKey, old.getId());
        verify(fileStore, never()).uploadUrl(anyString(), anyString(), anyLong());
        verify(auditEventService).record(eq("MEDICAL_CERTIFICATE_SUBMITTED"), any(), any(), eq("MEDICAL_CERTIFICATE"),
                eq(row.getId().toString()), eq("profileId=" + profile.getId() + ",evidence=DATE_ONLY,notices="));
    }

    @Test
    void aDateOnlySubmissionRefusesFileFields() {
        when(medicalRequirement.fileRequired(profile.getId())).thenReturn(false);

        assertCode(() -> service.submit(profile.getId(), request(LocalDate.of(2027, 5, 3), "image/jpeg", 1000), parent),
                "error.medical.file_not_requested");
        verify(certificateRepository, never()).save(any());
    }

    @Test
    void theResultAndItsDateAreChecked() {
        when(medicalRequirement.fileRequired(profile.getId())).thenReturn(false);
        assertCode(() -> service.submit(profile.getId(), dateOnly(null, LocalDate.of(2027, 5, 3)), parent), "validation.medical.result");
        assertCode(() -> service.submit(profile.getId(), dateOnly("FIT", LocalDate.of(2027, 5, 3)), parent), "validation.medical.result");
        assertCode(() -> service.submit(profile.getId(), dateOnly("IDEIGLENESEN_ELTILTVA", null), parent), "validation.medical.valid_until");
        assertCode(() -> service.submit(profile.getId(), dateOnly("NEM_ALKALMAS", LocalDate.of(2027, 5, 3)), parent),
                "validation.medical.valid_until_not_allowed");
        verify(certificateRepository, never()).save(any());

        service.submit(profile.getId(), dateOnly("VEGLEGESEN_ELTILTVA", null), parent);
        verify(certificateRepository).save(argThat(row -> row.getResult() == MedicalResult.VEGLEGESEN_ELTILTVA && row.getValidUntil() == null));
    }

    @Test
    void aPastPositiveDateCannotReplaceAGoodCertificateButANegativeResultCan() {
        when(medicalRequirement.fileRequired(profile.getId())).thenReturn(false);
        MedicalCertificate valid = current(LocalDate.of(2027, 5, 3));
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(valid));

        assertCode(() -> service.submit(profile.getId(), dateOnly("VERSENYEZHET", LocalDate.of(2026, 1, 1)), parent),
                "validation.medical.valid_until_past_replace");
        service.submit(profile.getId(), dateOnly("IDEIGLENESEN_ELTILTVA", LocalDate.of(2026, 1, 1)), parent);
        service.submit(profile.getId(), dateOnly("NEM_ALKALMAS", null), parent);
        verify(certificateRepository, org.mockito.Mockito.times(2)).save(argThat(row -> !row.getResult().isPositive()));
    }

    @Test
    void aFileSubmissionNeedsTheRedactionConfirmation() {
        assertCode(() -> service.submit(profile.getId(), new SubmitMedicalCertificateRequest("VERSENYEZHET",
                LocalDate.of(2027, 5, 3), true, "image/jpeg", 1000L, false), parent), "error.medical.redaction_required");
        verify(certificateRepository, never()).save(any());
    }

    @Test
    void aChildAskedForAFileWithADateOnlyRowGetsFileRequestedNextAndTheResultFlags() {
        MedicalCertificate dates = current(LocalDate.of(2027, 5, 3));
        dates.setObjectKey(null);
        dates.setEvidence(MedicalCertificate.Evidence.DATE_ONLY);
        dates.setResult(MedicalResult.ALKALMAS);
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(dates));

        MedicalStatusResponse status = service.getStatus(profile.getId(), parent);

        assertThat(status.fileRequired()).isTrue();
        assertThat(status.fileRequestedNext()).isTrue();
        assertThat(status.hasFile()).isFalse();
        assertThat(status.result()).isEqualTo("ALKALMAS");
        assertThat(status.resultAllowsTraining()).isTrue();
        assertThat(status.resultAllowsCompeting()).isFalse();
    }

    @Test
    void aClubAdminWhoseClubAsksForTheDateOnlyCannotOpenTheFile() {
        ClubAdmin clubAdmin = new ClubAdmin();
        clubAdmin.setId(UUID.randomUUID());
        clubAdmin.setRole(UserRole.CLUB_ADMIN);
        MedicalCertificate file = current(LocalDate.of(2027, 5, 3));
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(file));
        when(certificateRepository.findById(file.getId())).thenReturn(Optional.of(file));
        when(medicalAccess.managerRole(clubAdmin, profile.getId())).thenReturn(Optional.of(MedicalSubmitterRole.CLUB_ADMIN));

        assertThat(service.getStatus(profile.getId(), clubAdmin).hasFile()).isFalse();
        assertCode(() -> service.openFile(profile.getId(), file.getId(), clubAdmin), "error.auth.forbidden");

        when(medicalAccess.isClubAdminRequiringFile(clubAdmin, profile.getId())).thenReturn(true);
        assertThat(service.getStatus(profile.getId(), clubAdmin).hasFile()).isTrue();
        service.openFile(profile.getId(), file.getId(), clubAdmin);
        verify(fileStore).downloadUrl(file.getObjectKey(), file.getContentType());
    }

    private static SubmitMedicalCertificateRequest dateOnly(String result, LocalDate validUntil) {
        return new SubmitMedicalCertificateRequest(result, validUntil, true, null, null, null);
    }

    // ---------------------------------------------------------------- club conditions (PR 9)

    @Test
    void aClubThatIsNotReadyStopsSubmittingAndCompletingButNotWithdrawing() {
        Club club = requiringClub();
        when(compliance.isClubReady(club)).thenReturn(false);

        assertCode(() -> service.submit(profile.getId(), request(LocalDate.of(2027, 5, 3), "image/jpeg", 1000), parent),
                "error.medical.club_not_ready");
        verify(certificateRepository, never()).save(any());

        MedicalCertificate upload = pending(NOW.minusSeconds(30));
        when(certificateRepository.findById(upload.getId())).thenReturn(Optional.of(upload));
        assertCode(() -> service.complete(profile.getId(), upload.getId(), parent), "error.medical.club_not_ready");
        assertThat(upload.getCompletedAt()).isNull();

        MedicalCertificate current = current(LocalDate.of(2027, 5, 3));
        when(certificateRepository.findById(current.getId())).thenReturn(Optional.of(current));
        service.withdraw(profile.getId(), current.getId(), parent);
        assertThat(current.getWithdrawnAt()).isEqualTo(NOW);
    }

    @Test
    void theSubmissionAuditRecordsTheNoticeVersionsTheSubmitterWasShown() {
        Club club = requiringClub();
        club.setMedicalNoticeVersion(4);
        when(compliance.isClubReady(club)).thenReturn(true);
        MedicalCertificate upload = pending(NOW.minusSeconds(30));
        when(certificateRepository.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(fileStore.size(upload.getObjectKey())).thenReturn(Optional.of(1000L));
        when(fileStore.firstBytes(upload.getObjectKey(), 8)).thenReturn(Optional.of(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));

        service.complete(profile.getId(), upload.getId(), parent);

        verify(auditEventService).record(eq("MEDICAL_CERTIFICATE_SUBMITTED"), any(), any(), eq("MEDICAL_CERTIFICATE"), any(),
                eq("profileId=" + profile.getId() + ",evidence=FILE,notices=" + club.getId() + ":4"));
    }

    @Test
    void theFullStateListsTheRequiringClubsNoticesButTheCoachGetsNone() {
        Club club = requiringClub();
        club.setMedicalLegalBasis("2004. évi I. törvény");
        club.setMedicalNoticeUrl("https://kekfc.hu/adatkezeles");

        MedicalStatusResponse status = service.getStatus(profile.getId(), parent);
        assertThat(status.notices()).containsExactly(
                new MedicalNoticeItem(club.getId(), "Kék FC", "2004. évi I. törvény", "URL"));

        Coach coach = new Coach();
        coach.setId(UUID.randomUUID());
        coach.setRole(UserRole.COACH);
        when(medicalAccess.managerRole(coach, profile.getId())).thenReturn(Optional.empty());
        when(medicalAccess.isCoachRequiringProfile(coach, profile.getId())).thenReturn(true);
        assertThat(service.getStatus(profile.getId(), coach).notices()).isNull();
    }

    private Club requiringClub() {
        Club club = new Club();
        club.setId(UUID.randomUUID());
        club.setName("Kék FC");
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setClub(club);
        when(medicalRequirement.requiringTeams(profile.getId())).thenReturn(List.of(team));
        return club;
    }

    @Test
    void magicBytesMatchTheDeclaredType() {
        assertThat(MedicalCertificateService.startsLike("application/pdf", "%PDF-1.4".getBytes())).isTrue();
        assertThat(MedicalCertificateService.startsLike("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isTrue();
        assertThat(MedicalCertificateService.startsLike("image/jpeg", "%PDF-1.4".getBytes())).isFalse();
        assertThat(MedicalCertificateService.startsLike("image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47})).isFalse();
    }

    @Test
    void aPdfLeftPendingCannotTurnCurrent() {
        MedicalCertificate upload = pending(NOW.minusSeconds(30));
        upload.setContentType("application/pdf");
        when(certificateRepository.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(fileStore.size(upload.getObjectKey())).thenReturn(Optional.of(1000L));
        when(fileStore.firstBytes(upload.getObjectKey(), 8)).thenReturn(Optional.of("%PDF-1.7".getBytes()));

        assertCode(() -> service.complete(profile.getId(), upload.getId(), parent), "error.medical.file_mismatch");

        verify(certificateRepository).delete(upload);
        verify(fileStore).delete(upload.getObjectKey(), upload.getId());
    }

    @Test
    void aRawPdfOrPngIsRefusedEvenWithTheRedactionConfirmed() {
        for (String type : List.of("application/pdf", "image/png")) {
            assertCode(() -> service.submit(profile.getId(), request(LocalDate.of(2027, 5, 3), type, 1000), parent), "error.medical.file_type");
        }
        verify(certificateRepository, never()).save(any());
        verify(fileStore, never()).uploadUrl(anyString(), anyString(), anyLong());
    }

    // ---------------------------------------------------------------- withdraw, open

    @Test
    void withdrawClearsTheFileAndKeepsTheDateAsHistory() {
        MedicalCertificate certificate = current(LocalDate.of(2027, 5, 3));
        String key = certificate.getObjectKey();
        when(certificateRepository.findById(certificate.getId())).thenReturn(Optional.of(certificate));

        service.withdraw(profile.getId(), certificate.getId(), parent);

        assertThat(certificate.getWithdrawnAt()).isEqualTo(NOW);
        assertThat(certificate.getObjectKey()).isNull();
        assertThat(certificate.getValidUntil()).isEqualTo(LocalDate.of(2027, 5, 3));
        verify(fileStore).deleteAfterCommit(key, certificate.getId());
    }

    @Test
    void aSupersededCertificateCannotBeWithdrawnOrOpened() {
        MedicalCertificate old = current(LocalDate.of(2026, 5, 3));
        old.setSupersededAt(NOW.minusSeconds(10));
        when(certificateRepository.findById(old.getId())).thenReturn(Optional.of(old));

        assertCode(() -> service.withdraw(profile.getId(), old.getId(), parent), "error.medical.not_current");
        assertCode(() -> service.openFile(profile.getId(), old.getId(), parent), "error.medical.not_current");
    }

    @Test
    void inTheGraceTheParentAndTheAdultPlayerOpenTheFileAndNobodyElse() {
        MedicalCertificate certificate = current(LocalDate.of(2027, 5, 3));
        when(certificateRepository.findById(certificate.getId())).thenReturn(Optional.of(certificate));
        when(medicalRequirement.isRequired(profile.getId())).thenReturn(false);

        Player minor = new Player();
        minor.setId(UUID.randomUUID());
        when(medicalAccess.isUploadingPlayer(minor, profile)).thenReturn(true);
        assertCode(() -> service.openFile(profile.getId(), certificate.getId(), minor), "error.auth.forbidden");
        Parent coach = new Parent();
        coach.setId(UUID.randomUUID());
        when(medicalAccess.isCoachWithFileAccess(coach, profile.getId())).thenReturn(true);
        assertCode(() -> service.openFile(profile.getId(), certificate.getId(), coach), "error.auth.forbidden");
        verify(fileStore, never()).downloadUrl(any(), any());

        service.openFile(profile.getId(), certificate.getId(), parent);
        Player adult = new Player();
        adult.setId(UUID.randomUUID());
        when(medicalAccess.isAdultLinkedPlayer(adult, profile)).thenReturn(true);
        service.openFile(profile.getId(), certificate.getId(), adult);
        verify(fileStore, times(2)).downloadUrl(any(), any());
    }

    @Test
    void inTheGraceTheAdultPlayerGetsTheLeftoverFileOnItsOwnPage() {
        MedicalCertificate leftover = current(LocalDate.of(2027, 5, 3));
        Player adult = new Player();
        adult.setId(UUID.randomUUID());
        when(playerProfileRepository.findByLinkedUser_Id(adult.getId())).thenReturn(Optional.of(profile));
        when(medicalAccess.isAdultLinkedPlayer(adult, profile)).thenReturn(true);
        when(medicalRequirement.isRequired(profile.getId())).thenReturn(false);
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(leftover));

        MedicalStatusResponse mine = service.getMyStatus(adult);

        assertThat(mine.required()).isFalse();
        assertThat(mine.certificateId()).isEqualTo(leftover.getId());
        assertThat(mine.hasFile()).isTrue();
        assertThat(mine.profileId()).isEqualTo(profile.getId());
        assertThat(mine.validUntil()).isNull();
    }

    // ---------------------------------------------------------------- read

    @Test
    void aCoachGetsTheDateWithoutIdHistoryOrSubmitter() {
        Parent coach = new Parent();
        coach.setId(UUID.randomUUID());
        when(medicalAccess.managerRole(coach, profile.getId())).thenReturn(Optional.empty());
        when(medicalAccess.isCoachRequiringProfile(coach, profile.getId())).thenReturn(true);
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(verified(current(LocalDate.of(2026, 11, 9)))));

        MedicalStatusResponse status = service.getStatus(profile.getId(), coach);

        assertThat(status.status()).isEqualTo("EXPIRING");
        assertThat(status.verified()).isTrue();
        assertThat(status.verificationMethod()).isEqualTo("ORIGINAL");
        assertThat(status.result()).isEqualTo("VERSENYEZHET");
        assertThat(status.notices()).isNull();
        assertThat(status.daysRemaining()).isEqualTo(18);
        assertThat(status.certificateId()).isNull();
        assertThat(status.history()).isNull();
        assertThat(status.submittedByFirstName()).isNull();
    }

    @Test
    void aStrangerIsForbidden() {
        Parent stranger = new Parent();
        stranger.setId(UUID.randomUUID());
        when(medicalAccess.managerRole(stranger, profile.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStatus(profile.getId(), stranger)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void whenNoLongerRequiredTheParentGetsTheLeftoverFileAndAMinorPlayerDoesNot() {
        MedicalCertificate leftover = current(LocalDate.of(2027, 5, 3));
        when(medicalRequirement.isRequired(profile.getId())).thenReturn(false);
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(leftover));

        MedicalStatusResponse forParent = service.getStatus(profile.getId(), parent);
        assertThat(forParent.required()).isFalse();
        assertThat(forParent.certificateId()).isEqualTo(leftover.getId());
        assertThat(forParent.hasFile()).isTrue();
        assertThat(forParent.validUntil()).isNull();

        Player child = new Player();
        child.setId(UUID.randomUUID());
        profile.setLinkedUser(child);
        profile.setCanUploadMedical(true);
        when(medicalAccess.managerRole(child, profile.getId())).thenReturn(Optional.empty());
        when(medicalAccess.isUploadingPlayer(child, profile)).thenReturn(true);
        MedicalStatusResponse forChild = service.getStatus(profile.getId(), child);
        assertThat(forChild.required()).isFalse();
        assertThat(forChild.certificateId()).isNull();
    }

    @Test
    void theChildAccountWithoutTheRightSeesItsOwnStateReadOnlyWithoutTheFile() {
        Player child = new Player();
        child.setId(UUID.randomUUID());
        profile.setLinkedUser(child);
        when(playerProfileRepository.findByLinkedUser_Id(child.getId())).thenReturn(Optional.of(profile));
        when(medicalAccess.isLinkedPlayer(child, profile)).thenReturn(true);
        MedicalCertificate certificate = verified(current(LocalDate.of(2027, 5, 3)));
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(certificate));
        when(certificateRepository.findById(certificate.getId())).thenReturn(Optional.of(certificate));

        for (MedicalStatusResponse mine : List.of(service.getMyStatus(child), service.getStatus(profile.getId(), child))) {
            assertThat(mine.canUploadMedical()).isFalse();
            assertThat(mine.status()).isEqualTo("VALID");
            assertThat(mine.result()).isEqualTo("VERSENYEZHET");
            assertThat(mine.verified()).isTrue();
            assertThat(mine.verificationMethod()).isEqualTo("ORIGINAL");
            assertThat(mine.history()).isEmpty();
            assertThat(mine.certificateId()).isNull();
            assertThat(mine.hasFile()).isNull();
        }
        assertThat(service.getMyStatus(child).profileId()).isEqualTo(profile.getId());
        assertCode(() -> service.openFile(profile.getId(), certificate.getId(), child), "error.auth.forbidden");
        assertCode(() -> service.submit(profile.getId(), request(LocalDate.of(2027, 5, 3), "image/jpeg", 10), child),
                "error.auth.forbidden");
    }

    @Test
    void theChildAccountWithTheRightGetsItsProfileIdForUploading() {
        Player child = new Player();
        child.setId(UUID.randomUUID());
        profile.setLinkedUser(child);
        profile.setCanUploadMedical(true);
        when(playerProfileRepository.findByLinkedUser_Id(child.getId())).thenReturn(Optional.of(profile));

        MedicalStatusResponse mine = service.getMyStatus(child);

        assertThat(mine.canUploadMedical()).isTrue();
        assertThat(mine.profileId()).isEqualTo(profile.getId());
    }

    @Test
    void theParentSeesTheFullStateWithHistory() {
        MedicalCertificate certificate = verified(current(LocalDate.of(2027, 5, 3)));
        certificate.setSubmittedByUserId(parent.getId());
        parent.setFirstName("Anna");
        parent.setLastName("Kovács");
        MedicalCertificate history = current(LocalDate.of(2026, 4, 28));
        history.setSupersededAt(NOW.minusSeconds(100));
        history.setObjectKey(null);
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(certificate));
        when(certificateRepository.findHistoryByProfileId(profile.getId())).thenReturn(List.of(history));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(parent));

        MedicalStatusResponse status = service.getStatus(profile.getId(), parent);

        assertThat(status.status()).isEqualTo("VALID");
        assertThat(status.certificateId()).isEqualTo(certificate.getId());
        assertThat(status.hasFile()).isTrue();
        assertThat(status.submittedByLastName()).isEqualTo("Kovács");
        assertThat(status.history()).hasSize(1);
        assertThat(status.history().get(0).supersededAt()).isNotNull();
        assertThat(status.canUploadMedical()).isFalse();
    }

    // ---------------------------------------------------------------- platform admin

    @Test
    void thePlatformAdminHasNoMedicalViewOfItsOwn() {
        Admin operator = operator();
        when(medicalAccess.managerRole(operator, profile.getId())).thenReturn(Optional.empty());
        when(medicalAccess.submitterRole(operator, profile)).thenReturn(Optional.empty());
        MedicalCertificate certificate = current(LocalDate.of(2027, 5, 3));
        when(certificateRepository.findById(certificate.getId())).thenReturn(Optional.of(certificate));

        assertCode(() -> service.getStatus(profile.getId(), operator), "error.auth.forbidden");
        assertCode(() -> service.openFile(profile.getId(), certificate.getId(), operator), "error.auth.forbidden");
        assertCode(() -> service.withdraw(profile.getId(), certificate.getId(), operator), "error.auth.forbidden");
        assertCode(() -> service.submit(profile.getId(), request(LocalDate.of(2027, 5, 3), "image/jpeg", 1000), operator),
                "error.auth.forbidden");
        assertCode(() -> service.setUploadRight(profile.getId(), new SetMedicalUploadRightRequest(true), operator),
                "error.auth.forbidden");
        verify(fileStore, never()).downloadUrl(any(), any());
        assertThat(certificate.getWithdrawnAt()).isNull();
    }

    @Test
    void theTeamSummaryDoesNotLetThePlatformAdminThrough() {
        Admin operator = operator();
        Club club = new Club();
        club.setId(UUID.randomUUID());
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setClub(club);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(userAccessService.isAdmin(operator)).thenReturn(true);
        when(userAccessService.isClubAdmin(operator, club.getId())).thenReturn(false);

        assertCode(() -> service.teamStatus(team.getId(), operator), "error.auth.forbidden");
    }

    @Test
    void breakGlassNeedsAReasonOfTwentyToFiveHundredCharacters() {
        Admin operator = operator();
        when(userAccessService.isAdmin(operator)).thenReturn(true);

        assertCode(() -> service.adminAccess(profile.getId(), new MedicalAdminAccessRequest("too short"), operator),
                "error.medical.admin_reason");
        assertCode(() -> service.adminAccess(profile.getId(), new MedicalAdminAccessRequest("x".repeat(501)), operator),
                "error.medical.admin_reason");
        assertCode(() -> service.adminAccess(profile.getId(), null, operator), "error.medical.admin_reason");
        verify(adminAccessRepository, never()).save(any());
    }

    @Test
    void breakGlassIsOnlyForThePlatformAdmin() {
        assertCode(() -> service.adminAccess(profile.getId(), new MedicalAdminAccessRequest(REASON), parent),
                "error.auth.forbidden");
        assertCode(() -> service.adminWithdraw(profile.getId(), new MedicalAdminWithdrawalRequest(UUID.randomUUID(), REASON), parent),
                "error.auth.forbidden");
    }

    @Test
    void thePlatformAdminGetsTechnicalFactsOnlyAndWritesAReadRow() {
        Admin operator = operator();
        when(userAccessService.isAdmin(operator)).thenReturn(true);
        profile.setFirstName("Bence");
        profile.setLastName("Kiss");
        UUID clubA = UUID.randomUUID();
        when(medicalRequirement.requiringClubIds(profile.getId())).thenReturn(Set.of(clubA));
        MedicalCertificate certificate = current(LocalDate.of(2027, 5, 3));
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(certificate));
        when(adminAccessRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MedicalAdminAccessResponse response = service.adminAccess(profile.getId(), new MedicalAdminAccessRequest("  " + REASON + "  "), operator);

        assertThat(response.certificateId()).isEqualTo(certificate.getId());
        assertThat(response.hasFile()).isTrue();
        assertThat(response.evidence()).isEqualTo("FILE");
        assertThat(response.contentType()).isEqualTo("image/jpeg");
        assertThat(response.sizeBytes()).isEqualTo(1000L);
        assertThat(response.submittedAt()).isEqualTo(certificate.getCompletedAt());
        // The result, the validity and the file never leave here, whatever the JSON looks like.
        assertThat(response.toString()).doesNotContain("VERSENYEZHET").doesNotContain("2027-05-03").doesNotContain(certificate.getObjectKey());
        verify(fileStore, never()).downloadUrl(any(), any());
        MedicalAdminAccess row = savedAccess();
        assertThat(row.getAction()).isEqualTo(MedicalAdminAccess.Action.READ);
        assertThat(row.getReason()).isEqualTo(REASON);
        assertThat(row.getPlayerName()).isEqualTo("Kiss Bence");
        assertThat(row.getClubIds()).containsExactly(clubA);
        assertThat(row.getCertificateId()).isEqualTo(certificate.getId());
        assertThat(row.getAdminUserId()).isEqualTo(operator.getId());
        verify(auditEventService).record(eq("MEDICAL_STATUS_READ_BY_ADMIN"), eq(operator.getId()), any(), eq("MEDICAL_CERTIFICATE"),
                eq(certificate.getId().toString()), argThat(details -> !details.contains(REASON)));
    }

    @Test
    void aPlatformAdminWithAClubAdminRowDoesNotReachTheTeamSummary() {
        Admin operator = operator();
        Club club = new Club();
        club.setId(UUID.randomUUID());
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setClub(club);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(medicalAccess.isAdmin(operator)).thenReturn(true);
        when(userAccessService.isClubAdmin(operator, club.getId())).thenReturn(true);

        assertCode(() -> service.teamStatus(team.getId(), operator), "error.auth.forbidden");
    }

    @Test
    void inTheGracePeriodTheRowGoesToTheClubsThatLastRequiredTheCertificate() {
        Admin operator = operator();
        when(userAccessService.isAdmin(operator)).thenReturn(true);
        UUID lastClub = UUID.randomUUID();
        profile.setMedicalLastRequiredClubIds(new ArrayList<>(List.of(lastClub)));
        when(medicalRequirement.isRequired(profile.getId())).thenReturn(false);
        when(medicalRequirement.requiringClubIds(profile.getId())).thenReturn(Set.of());
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(current(LocalDate.of(2027, 5, 3))));
        when(adminAccessRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MedicalAdminAccessResponse response = service.adminAccess(profile.getId(), new MedicalAdminAccessRequest(REASON), operator);

        assertThat(response.required()).isFalse();
        assertThat(response.notRequiredSince()).isEqualTo(profile.getMedicalNotRequiredSince());
        assertThat(savedAccess().getClubIds()).containsExactly(lastClub);
    }

    @Test
    void theAdminWithdrawalWithdrawsLikeTheParentAndWritesAWithdrawnRow() {
        Admin operator = operator();
        when(userAccessService.isAdmin(operator)).thenReturn(true);
        MedicalCertificate certificate = current(LocalDate.of(2027, 5, 3));
        String key = certificate.getObjectKey();
        when(certificateRepository.findById(certificate.getId())).thenReturn(Optional.of(certificate));
        when(adminAccessRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.adminWithdraw(profile.getId(), new MedicalAdminWithdrawalRequest(certificate.getId(), REASON), operator);

        assertThat(certificate.getWithdrawnAt()).isEqualTo(NOW);
        assertThat(certificate.getObjectKey()).isNull();
        verify(fileStore).deleteAfterCommit(key, certificate.getId());
        MedicalAdminAccess row = savedAccess();
        assertThat(row.getAction()).isEqualTo(MedicalAdminAccess.Action.WITHDRAWN);
        assertThat(row.getCertificateId()).isEqualTo(certificate.getId());
        verify(auditEventService).record(eq("MEDICAL_CERTIFICATE_WITHDRAWN_BY_ADMIN"), any(), any(), any(), any(), any());

        assertCode(() -> service.adminWithdraw(profile.getId(), new MedicalAdminWithdrawalRequest(certificate.getId(), "short"), operator),
                "error.medical.admin_reason");
    }

    @Test
    void theParentSeesTheAccessesNoClubSeesAndOthersDoNot() {
        MedicalAdminAccess row = new MedicalAdminAccess();
        row.setId(UUID.randomUUID());
        row.setAction(MedicalAdminAccess.Action.READ);
        row.setReason(REASON);
        row.setCreatedAt(NOW);
        row.setPlayerName("Kiss Bence");
        when(adminAccessRepository.findWithoutClubByProfileId(profile.getId())).thenReturn(List.of(row));
        when(medicalAccess.isParent(parent, profile.getId())).thenReturn(true);

        MedicalStatusResponse forParent = service.getStatus(profile.getId(), parent);

        assertThat(forParent.adminAccesses()).hasSize(1);
        assertThat(forParent.adminAccesses().get(0).reason()).isEqualTo(REASON);
        assertThat(forParent.adminAccesses().get(0).action()).isEqualTo("READ");
        assertThat(forParent.adminAccesses().get(0).playerName()).isNull();

        Parent coach = new Parent();
        coach.setId(UUID.randomUUID());
        when(medicalAccess.managerRole(coach, profile.getId())).thenReturn(Optional.empty());
        when(medicalAccess.isCoachRequiringProfile(coach, profile.getId())).thenReturn(true);
        assertThat(service.getStatus(profile.getId(), coach).adminAccesses()).isNull();
    }

    @Test
    void theClubListIsForTheClubsOwnAdminsAndKeepsTheChildsNameAfterTheProfileIsGone() {
        UUID clubId = UUID.randomUUID();
        Admin operator = operator();
        operator.setFirstName("Olga");
        operator.setLastName("Üzem");
        MedicalAdminAccess row = new MedicalAdminAccess();
        row.setId(UUID.randomUUID());
        row.setPlayerProfileId(UUID.randomUUID());
        row.setPlayerName("Kiss Bence");
        row.setAdminUserId(operator.getId());
        row.setAction(MedicalAdminAccess.Action.WITHDRAWN);
        row.setReason(REASON);
        row.setCreatedAt(NOW);
        when(adminAccessRepository.findByClubId(eq(clubId), any())).thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(operator));

        PageResponse<MedicalAdminAccessItem> page = service.clubAdminAccesses(clubId, null, null, parent);

        verify(userAccessService).requireOwnClubAdmin(parent, clubId);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items().get(0).playerName()).isEqualTo("Kiss Bence");
        assertThat(page.items().get(0).adminLastName()).isEqualTo("Üzem");
        assertThat(page.items().get(0).action()).isEqualTo("WITHDRAWN");

        doThrow(new ForbiddenException("error.auth.forbidden", new Object[0], "Only the club's own admins can do this."))
                .when(userAccessService).requireOwnClubAdmin(operator, clubId);
        assertCode(() -> service.clubAdminAccesses(clubId, null, null, operator), "error.auth.forbidden");
    }

    private Admin operator() {
        Admin operator = new Admin();
        operator.setId(UUID.randomUUID());
        operator.setRole(UserRole.ADMIN);
        return operator;
    }

    private MedicalAdminAccess savedAccess() {
        ArgumentCaptor<MedicalAdminAccess> captor = ArgumentCaptor.forClass(MedicalAdminAccess.class);
        verify(adminAccessRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------- upload right

    @Test
    void theUploadRightIsSetByAManagerAndLeavesTheFileAlone() {
        var response = service.setUploadRight(profile.getId(), new SetMedicalUploadRightRequest(true), parent);

        assertThat(response.allowed()).isTrue();
        assertThat(profile.isCanUploadMedical()).isTrue();
        verify(fileStore, never()).deleteAfterCommit(any(), any());
    }

    @Test
    void someoneElseCannotSetTheUploadRight() {
        Parent other = new Parent();
        other.setId(UUID.randomUUID());
        when(medicalAccess.managerRole(other, profile.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setUploadRight(profile.getId(), new SetMedicalUploadRightRequest(true), other))
                .isInstanceOf(ForbiddenException.class);
        assertThat(profile.isCanUploadMedical()).isFalse();
    }

    // ---------------------------------------------------------------- helpers

    private static SubmitMedicalCertificateRequest request(LocalDate validUntil, String type, long size) {
        return new SubmitMedicalCertificateRequest("VERSENYEZHET", validUntil, true, type, size, true);
    }

    private MedicalCertificate pending(Instant createdAt) {
        MedicalCertificate row = new MedicalCertificate();
        row.setId(UUID.randomUUID());
        row.setPlayerProfileId(profile.getId());
        row.setObjectKey("medical/" + profile.getId() + "/" + row.getId());
        row.setContentType("image/jpeg");
        row.setSizeBytes(1000L);
        row.setValidUntil(LocalDate.of(2027, 5, 3));
        row.setResult(MedicalResult.VERSENYEZHET);
        row.setEvidence(MedicalCertificate.Evidence.FILE);
        row.setAcknowledgementVersion(MedicalCertificate.ACKNOWLEDGEMENT_FILE);
        row.setSubmittedByRole(MedicalSubmitterRole.PARENT);
        row.setCreatedAt(createdAt);
        return row;
    }

    private MedicalCertificate current(LocalDate validUntil) {
        MedicalCertificate row = pending(NOW.minusSeconds(86_400));
        row.setValidUntil(validUntil);
        row.setCompletedAt(NOW.minusSeconds(86_000));
        return row;
    }

    private static MedicalCertificate verified(MedicalCertificate row) {
        row.setVerifiedAt(NOW.minusSeconds(3_600));
        row.setVerifiedByRole(MedicalSubmitterRole.COACH);
        row.setVerificationMethod(MedicalCertificate.VerificationMethod.ORIGINAL);
        return row;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOfAny(BadRequestException.class, ConflictException.class, ForbiddenException.class, NotFoundException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getMessageCode()).isEqualTo(code));
    }
}
