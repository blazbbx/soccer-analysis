package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.AppException;
import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.MedicalCertificate;
import com.example.footballanalysis.model.db.MedicalEvidence;
import com.example.footballanalysis.model.db.MedicalMode;
import com.example.footballanalysis.model.db.MedicalRejectionReason;
import com.example.footballanalysis.model.db.MedicalResult;
import com.example.footballanalysis.model.db.MedicalSubmitterRole;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.user.Admin;
import com.example.footballanalysis.model.db.user.ClubAdmin;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.MedicalInPersonRequest;
import com.example.footballanalysis.model.requests.MedicalDateCorrectionRequest;
import com.example.footballanalysis.model.requests.MedicalRejectionRequest;
import com.example.footballanalysis.model.requests.MedicalVerificationRequest;
import com.example.footballanalysis.model.requests.SubmitMedicalCertificateRequest;
import com.example.footballanalysis.model.responses.ClubMedicalStatusItem;
import com.example.footballanalysis.model.responses.ClubMedicalStatusResponse;
import com.example.footballanalysis.repository.AuditEventRepository;
import com.example.footballanalysis.repository.MedicalAdminAccessRepository;
import com.example.footballanalysis.repository.MedicalCertificateRepository;
import com.example.footballanalysis.repository.MedicalParentSeenRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.TeamRosterRepository;
import com.example.footballanalysis.repository.UserRepository;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verification, in-person entry, the coach's file access and the club list (docs/sportorvosi-jogszeruseg.md, PR 11).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MedicalVerificationTest {

    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");
    private static final LocalDate UNTIL = LocalDate.of(2027, 5, 3);

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
    private Coach coach;
    private ClubAdmin clubAdmin;
    private Parent parent;

    @BeforeEach
    void setUp() {
        service = new MedicalCertificateService(certificateRepository, adminAccessRepository, playerProfileRepository, teamRepository,
                teamRosterRepository, teamMembershipQuery, userRepository, userAccessService, medicalAccess, medicalRequirement,
                fileStore, storageGuard, compliance, auditEventService, auditEventRepository, parentSeenRepository,
                Clock.fixed(NOW, ZoneId.of("Europe/Budapest")), transactionManager);
        profile = new PlayerProfile();
        profile.setId(UUID.randomUUID());
        coach = new Coach();
        coach.setId(UUID.randomUUID());
        coach.setRole(UserRole.COACH);
        clubAdmin = new ClubAdmin();
        clubAdmin.setId(UUID.randomUUID());
        clubAdmin.setRole(UserRole.CLUB_ADMIN);
        parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setRole(UserRole.PARENT);
        when(playerProfileRepository.findById(profile.getId())).thenReturn(Optional.of(profile));
        when(playerProfileRepository.findByIdForUpdate(profile.getId())).thenReturn(Optional.of(profile));
        when(medicalRequirement.isRequired(profile.getId())).thenReturn(true);
        when(medicalAccess.isCoachRequiringProfile(coach, profile.getId())).thenReturn(true);
        when(medicalAccess.isClubAdminRequiringProfile(clubAdmin, profile.getId())).thenReturn(true);
        when(medicalAccess.managerRole(clubAdmin, profile.getId())).thenReturn(Optional.of(MedicalSubmitterRole.CLUB_ADMIN));
        when(medicalAccess.managerRole(coach, profile.getId())).thenReturn(Optional.empty());
        when(medicalAccess.managerRole(parent, profile.getId())).thenReturn(Optional.of(MedicalSubmitterRole.PARENT));
        when(medicalAccess.isParent(parent, profile.getId())).thenReturn(true);
        when(medicalAccess.isParentOfRecord(parent, profile.getId())).thenReturn(true);
        // Children are minors unless a test says otherwise.
        when(medicalAccess.isMinor(any())).thenReturn(true);
        when(certificateRepository.findHistoryByProfileId(profile.getId())).thenReturn(List.of());
        when(compliance.isClubReady(any())).thenReturn(true);
    }

    // ---------------------------------------------------------------- reject, correct the date

    @Test
    void aRejectedFileRowIsWithdrawnItsFileDeletedAndTheParentSeesTheReason() {
        MedicalCertificate row = current(true);
        String key = row.getObjectKey();
        when(medicalRequirement.fileRequired(profile.getId())).thenReturn(true);
        when(medicalAccess.isClubAdminRequiringFile(clubAdmin, profile.getId())).thenReturn(true);
        MedicalRejectionRequest request = new MedicalRejectionRequest("VERSENYEZHET", UNTIL, "FILE", "NOT_REDACTED");

        assertCode(() -> service.reject(profile.getId(), request, clubAdmin), "error.medical.file_not_opened");
        when(auditEventRepository.existsByActorSince(eq("MEDICAL_CERTIFICATE_OPENED"), eq(clubAdmin.getId()), eq(row.getId().toString()), any()))
                .thenReturn(true);
        service.reject(profile.getId(), request, clubAdmin);

        assertThat(row.isCurrent()).isFalse();
        assertThat(row.getObjectKey()).isNull();
        assertThat(row.getRejectionReason()).isEqualTo(MedicalRejectionReason.NOT_REDACTED);
        assertThat(row.getRejectedByRole()).isEqualTo(MedicalSubmitterRole.CLUB_ADMIN);
        verify(fileStore).deleteAfterCommit(key, row.getId());
        verify(auditEventService).record(eq("MEDICAL_CERTIFICATE_REJECTED"), eq(clubAdmin.getId()), any(), eq("MEDICAL_CERTIFICATE"),
                eq(row.getId().toString()), eq("profileId=" + profile.getId() + ",method=FILE,reason=NOT_REDACTED"));

        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.empty());
        when(certificateRepository.findHistoryByProfileId(profile.getId())).thenReturn(List.of(row));
        var status = service.getStatus(profile.getId(), parent);
        assertThat(status.status()).isEqualTo("MISSING");
        assertThat(status.rejectionReason()).isEqualTo("NOT_REDACTED");
        assertThat(status.rejectedByRole()).isEqualTo("CLUB_ADMIN");
        assertThat(status.history().get(0).rejectedAt()).isEqualTo(NOW);
    }

    @Test
    void aRejectionNeedsAKnownReasonAnUnverifiedRowAndTheRowOnTheScreen() {
        MedicalCertificate row = current(false);

        assertCode(() -> service.reject(profile.getId(), new MedicalRejectionRequest("VERSENYEZHET", UNTIL, "ORIGINAL", "LATE"), coach),
                "error.medical.rejection_reason");
        assertCode(() -> service.reject(profile.getId(), new MedicalRejectionRequest("ALKALMAS", UNTIL, "ORIGINAL", "OTHER"), coach),
                "error.medical.verification_changed");
        assertThatThrownBy(() -> service.reject(profile.getId(), new MedicalRejectionRequest("VERSENYEZHET", UNTIL, "ORIGINAL", "OTHER"), parent))
                .isInstanceOf(ForbiddenException.class);
        row.setVerifiedAt(NOW);
        assertCode(() -> service.reject(profile.getId(), new MedicalRejectionRequest("VERSENYEZHET", UNTIL, "ORIGINAL", "OTHER"), coach),
                "error.medical.already_verified");
        assertThat(row.isCurrent()).isTrue();
    }

    @Test
    void theCoachCorrectsTheDateItVerifiesTheRowAndTheParentIsTold() {
        MedicalCertificate row = current(false);
        LocalDate corrected = UNTIL.plusMonths(1);
        MedicalDateCorrectionRequest request = new MedicalDateCorrectionRequest("VERSENYEZHET", UNTIL, "ORIGINAL", corrected);
        row.setSubmittedByUserId(parent.getId());
        assertThat(MedicalCertificateService.isUnseen(parent.getId(), row, null, List.of())).isFalse();

        var status = service.correctDate(profile.getId(), request, coach);

        assertThat(row.getValidUntil()).isEqualTo(corrected);
        assertThat(row.getOriginalValidUntil()).isEqualTo(UNTIL);
        assertThat(row.getDateCorrectedByRole()).isEqualTo(MedicalSubmitterRole.COACH);
        assertThat(row.getVerifiedByUserId()).isEqualTo(coach.getId());
        assertThat(row.getVerificationMethod()).isEqualTo(MedicalCertificate.VerificationMethod.ORIGINAL);
        assertThat(status.verified()).isTrue();
        assertThat(status.validUntil()).isEqualTo(corrected);
        assertThat(MedicalCertificateService.isUnseen(parent.getId(), row, null, List.of())).isTrue();
        verify(auditEventService).record(eq("MEDICAL_CERTIFICATE_DATE_CORRECTED"), eq(coach.getId()), any(), eq("MEDICAL_CERTIFICATE"),
                eq(row.getId().toString()), eq("profileId=" + profile.getId() + ",method=ORIGINAL"));
    }

    @Test
    void aDateCorrectionNeedsADatedResultANewDateAndTheOpenedFile() {
        MedicalCertificate row = current(false);
        assertCode(() -> service.correctDate(profile.getId(), new MedicalDateCorrectionRequest("VERSENYEZHET", UNTIL, "ORIGINAL", UNTIL), coach),
                "error.medical.date_unchanged");
        assertCode(() -> service.correctDate(profile.getId(), new MedicalDateCorrectionRequest("VERSENYEZHET", UNTIL, "ORIGINAL", null), coach),
                "validation.medical.valid_until");

        row.setResult(MedicalResult.NEM_ALKALMAS);
        row.setValidUntil(null);
        assertCode(() -> service.correctDate(profile.getId(), new MedicalDateCorrectionRequest("NEM_ALKALMAS", null, "ORIGINAL", UNTIL), coach),
                "validation.medical.valid_until_not_allowed");

        current(true);
        when(medicalRequirement.fileRequired(profile.getId())).thenReturn(true);
        when(medicalAccess.isCoachWithFileAccess(coach, profile.getId())).thenReturn(true);
        assertCode(() -> service.correctDate(profile.getId(), new MedicalDateCorrectionRequest("VERSENYEZHET", UNTIL, "FILE", UNTIL.plusDays(1)), coach),
                "error.medical.file_not_opened");
    }

    // ---------------------------------------------------------------- verify, date-only rows

    @Test
    void theCoachVerifiesADateOnlyRowFromTheOriginalAndTheLightTurnsGreen() {
        MedicalCertificate row = current(false);

        var status = service.verify(profile.getId(), new MedicalVerificationRequest("VERSENYEZHET", UNTIL, "ORIGINAL"), coach);

        assertThat(row.getVerifiedAt()).isEqualTo(NOW);
        assertThat(row.getVerifiedByUserId()).isEqualTo(coach.getId());
        assertThat(row.getVerifiedByRole()).isEqualTo(MedicalSubmitterRole.COACH);
        assertThat(row.getVerificationMethod()).isEqualTo(MedicalCertificate.VerificationMethod.ORIGINAL);
        assertThat(status.status()).isEqualTo("VALID");
        assertThat(status.verified()).isTrue();
        verify(auditEventService).record(eq("MEDICAL_CERTIFICATE_VERIFIED"), eq(coach.getId()), any(), eq("MEDICAL_CERTIFICATE"),
                eq(row.getId().toString()), eq("profileId=" + profile.getId() + ",method=ORIGINAL"));
    }

    @Test
    void aDateOnlyRowRefusesTheFileMethodAndTheClubAdminWinsOverTheCoach() {
        current(false);
        assertCode(() -> service.verify(profile.getId(), new MedicalVerificationRequest("VERSENYEZHET", UNTIL, "FILE"), coach),
                "error.medical.verification_method");

        when(medicalAccess.isCoachRequiringProfile(clubAdmin, profile.getId())).thenReturn(true);
        MedicalCertificate row = current(false);
        service.verify(profile.getId(), new MedicalVerificationRequest("VERSENYEZHET", UNTIL, "ORIGINAL"), clubAdmin);
        assertThat(row.getVerifiedByRole()).isEqualTo(MedicalSubmitterRole.CLUB_ADMIN);
    }

    @Test
    void theParentThePlayerAndThePlatformAdminCannotVerify() {
        current(false);
        Admin operator = new Admin();
        operator.setId(UUID.randomUUID());
        operator.setRole(UserRole.ADMIN);
        for (var actor : List.of(parent, operator)) {
            assertThatThrownBy(() -> service.verify(profile.getId(), new MedicalVerificationRequest("VERSENYEZHET", UNTIL, "ORIGINAL"), actor))
                    .isInstanceOf(ForbiddenException.class);
        }
        verify(certificateRepository, never()).save(any());
    }

    @Test
    void aChangedCertificateAnOldRowWithoutAResultOrNoRowAtAllCannotBeVerified() {
        current(false);
        assertCode(() -> service.verify(profile.getId(), new MedicalVerificationRequest("ALKALMAS", UNTIL, "ORIGINAL"), coach),
                "error.medical.verification_changed");
        assertCode(() -> service.verify(profile.getId(), new MedicalVerificationRequest("VERSENYEZHET", UNTIL.plusDays(1), "ORIGINAL"), coach),
                "error.medical.verification_changed");

        MedicalCertificate old = current(false);
        old.setResult(null);
        assertCode(() -> service.verify(profile.getId(), new MedicalVerificationRequest("VERSENYEZHET", UNTIL, "ORIGINAL"), coach),
                "error.medical.result_missing");

        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.empty());
        assertCode(() -> service.verify(profile.getId(), new MedicalVerificationRequest("VERSENYEZHET", UNTIL, "ORIGINAL"), coach),
                "error.medical.not_current");
    }

    @Test
    void anAlreadyVerifiedRowStaysAsItIs() {
        MedicalCertificate row = current(false);
        UUID first = UUID.randomUUID();
        row.setVerifiedAt(NOW.minusSeconds(60));
        row.setVerifiedByUserId(first);
        row.setVerifiedByRole(MedicalSubmitterRole.CLUB_ADMIN);
        row.setVerificationMethod(MedicalCertificate.VerificationMethod.ORIGINAL);

        service.verify(profile.getId(), new MedicalVerificationRequest("VERSENYEZHET", UNTIL, "ORIGINAL"), coach);

        assertThat(row.getVerifiedByUserId()).isEqualTo(first);
        verify(certificateRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- verify, file rows

    @Test
    void aFileRowIsVerifiedFromTheFileOnlyAfterTheCallerOpenedItWithinThirtyMinutes() {
        MedicalCertificate row = current(true);
        when(medicalRequirement.fileRequired(profile.getId())).thenReturn(true);
        when(medicalAccess.isClubAdminRequiringFile(clubAdmin, profile.getId())).thenReturn(true);

        assertCode(() -> service.verify(profile.getId(), new MedicalVerificationRequest("VERSENYEZHET", UNTIL, "ORIGINAL"), clubAdmin),
                "error.medical.verification_method");
        assertCode(() -> service.verify(profile.getId(), new MedicalVerificationRequest("VERSENYEZHET", UNTIL, "FILE"), clubAdmin),
                "error.medical.file_not_opened");

        LocalDateTime since = LocalDateTime.ofInstant(NOW.minus(Duration.ofMinutes(30)), ZoneId.systemDefault());
        when(auditEventRepository.existsByActorSince("MEDICAL_CERTIFICATE_OPENED", clubAdmin.getId(), row.getId().toString(), since))
                .thenReturn(true);
        service.verify(profile.getId(), new MedicalVerificationRequest("VERSENYEZHET", UNTIL, "FILE"), clubAdmin);
        assertThat(row.getVerificationMethod()).isEqualTo(MedicalCertificate.VerificationMethod.FILE);
        assertThat(row.getVerifiedByRole()).isEqualTo(MedicalSubmitterRole.CLUB_ADMIN);
    }

    @Test
    void anotherUsersOpeningDoesNotCount() {
        MedicalCertificate row = current(true);
        when(medicalRequirement.fileRequired(profile.getId())).thenReturn(true);
        when(medicalAccess.isClubAdminRequiringFile(clubAdmin, profile.getId())).thenReturn(true);
        when(auditEventRepository.existsByActorSince(eq("MEDICAL_CERTIFICATE_OPENED"), eq(parent.getId()), eq(row.getId().toString()), any()))
                .thenReturn(true);

        assertCode(() -> service.verify(profile.getId(), new MedicalVerificationRequest("VERSENYEZHET", UNTIL, "FILE"), clubAdmin),
                "error.medical.file_not_opened");
    }

    @Test
    void aCoachVerifiesAFileRowOnlyWithTheClubsFileAccess() {
        MedicalCertificate row = current(true);
        when(medicalRequirement.fileRequired(profile.getId())).thenReturn(true);
        when(auditEventRepository.existsByActorSince(any(), eq(coach.getId()), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.verify(profile.getId(), new MedicalVerificationRequest("VERSENYEZHET", UNTIL, "FILE"), coach))
                .isInstanceOf(ForbiddenException.class);

        when(medicalAccess.isCoachWithFileAccess(coach, profile.getId())).thenReturn(true);
        service.verify(profile.getId(), new MedicalVerificationRequest("VERSENYEZHET", UNTIL, "FILE"), coach);
        assertThat(row.getVerifiedByRole()).isEqualTo(MedicalSubmitterRole.COACH);
        assertThat(row.getVerificationMethod()).isEqualTo(MedicalCertificate.VerificationMethod.FILE);
    }

    @Test
    void aFileRowWhoseFileWasRemovedByPolicyIsVerifiedFromTheOriginal() {
        MedicalCertificate row = current(true);
        row.setObjectKey(null);
        when(medicalRequirement.fileRequired(profile.getId())).thenReturn(true);

        assertCode(() -> service.verify(profile.getId(), new MedicalVerificationRequest("VERSENYEZHET", UNTIL, "FILE"), coach),
                "error.medical.verification_method");
        service.verify(profile.getId(), new MedicalVerificationRequest("VERSENYEZHET", UNTIL, "ORIGINAL"), coach);
        assertThat(row.getVerificationMethod()).isEqualTo(MedicalCertificate.VerificationMethod.ORIGINAL);
    }

    // ---------------------------------------------------------------- unverify

    @Test
    void aFileVerificationIsTakenBackOnlyByWhoCouldVerifyItFromTheFile() {
        MedicalCertificate row = current(true);
        when(medicalRequirement.fileRequired(profile.getId())).thenReturn(true);
        row.setVerifiedAt(NOW.minusSeconds(60));
        row.setVerifiedByRole(MedicalSubmitterRole.CLUB_ADMIN);
        row.setVerificationMethod(MedicalCertificate.VerificationMethod.FILE);

        assertThatThrownBy(() -> service.unverify(profile.getId(), coach)).isInstanceOf(ForbiddenException.class);
        assertThat(row.isVerified()).isTrue();

        when(medicalAccess.isClubAdminRequiringFile(clubAdmin, profile.getId())).thenReturn(true);
        service.unverify(profile.getId(), clubAdmin);
        assertThat(row.getVerifiedAt()).isNull();
        assertThat(row.getVerifiedByRole()).isNull();
        assertThat(row.getVerificationMethod()).isNull();
        verify(auditEventService).record(eq("MEDICAL_CERTIFICATE_UNVERIFIED"), any(), any(), eq("MEDICAL_CERTIFICATE"), any(), any());
        // Taking back needs no fresh opening.
        verify(auditEventRepository, never()).existsByActorSince(any(), any(), any(), any());
    }

    @Test
    void anOriginalVerificationIsTakenBackByTheCoachOrTheClubAdmin() {
        MedicalCertificate row = current(false);
        row.setVerifiedAt(NOW.minusSeconds(60));
        row.setVerifiedByRole(MedicalSubmitterRole.CLUB_ADMIN);
        row.setVerificationMethod(MedicalCertificate.VerificationMethod.ORIGINAL);

        service.unverify(profile.getId(), coach);

        assertThat(row.isVerified()).isFalse();
        assertThatThrownBy(() -> service.unverify(profile.getId(), parent)).isInstanceOf(ForbiddenException.class);
    }

    // ---------------------------------------------------------------- in person

    @Test
    void theCoachEntersACertificateSeenInPersonAndItIsVerifiedAtOnce() {
        requiringClub();
        MedicalCertificate old = current(true);
        String oldKey = old.getObjectKey();

        var status = service.submitInPerson(profile.getId(), new MedicalInPersonRequest("ALKALMAS", UNTIL, true), coach);

        ArgumentCaptor<MedicalCertificate> saved = ArgumentCaptor.forClass(MedicalCertificate.class);
        verify(certificateRepository).save(saved.capture());
        MedicalCertificate row = saved.getValue();
        assertThat(row.getSubmittedByRole()).isEqualTo(MedicalSubmitterRole.COACH);
        assertThat(row.getSubmittedByUserId()).isEqualTo(coach.getId());
        assertThat(row.getEvidence()).isEqualTo(MedicalCertificate.Evidence.DATE_ONLY);
        assertThat(row.getAcknowledgementVersion()).isEqualTo("sport-medical-v2-in-person");
        assertThat(row.getVerifiedByUserId()).isEqualTo(coach.getId());
        assertThat(row.getVerifiedByRole()).isEqualTo(MedicalSubmitterRole.COACH);
        assertThat(row.getVerificationMethod()).isEqualTo(MedicalCertificate.VerificationMethod.ORIGINAL);
        assertThat(row.getCompletedAt()).isEqualTo(NOW);
        verify(certificateRepository).saveAndFlush(old);
        verify(fileStore).deleteAfterCommit(oldKey, old.getId());
        assertThat(status).isNotNull();
        verify(auditEventService).record(eq("MEDICAL_CERTIFICATE_SUBMITTED"), any(), any(), eq("MEDICAL_CERTIFICATE"), any(),
                argThat(details -> details.contains("evidence=DATE_ONLY") && details.contains("inPerson=true")));
    }

    @Test
    void theClubAdminEntersInPersonAsClubAdmin() {
        current(false);
        when(medicalAccess.isCoachRequiringProfile(clubAdmin, profile.getId())).thenReturn(true);

        service.submitInPerson(profile.getId(), new MedicalInPersonRequest("NEM_ALKALMAS", null, true), clubAdmin);

        verify(certificateRepository).save(argThat(row -> row.getSubmittedByRole() == MedicalSubmitterRole.CLUB_ADMIN
                && row.getResult() == MedicalResult.NEM_ALKALMAS));
    }

    @Test
    void inPersonNeedsTheConfirmationAReadyClubAndACoachOrClubAdmin() {
        current(false);
        assertCode(() -> service.submitInPerson(profile.getId(), new MedicalInPersonRequest("VERSENYEZHET", UNTIL, false), coach),
                "error.medical.seen_original_required");

        Club club = requiringClub();
        when(compliance.isClubReady(club)).thenReturn(false);
        assertCode(() -> service.submitInPerson(profile.getId(), new MedicalInPersonRequest("VERSENYEZHET", UNTIL, true), coach),
                "error.medical.club_not_ready");
        when(compliance.isClubReady(club)).thenReturn(true);

        Admin operator = new Admin();
        operator.setId(UUID.randomUUID());
        operator.setRole(UserRole.ADMIN);
        for (var actor : List.of(parent, operator)) {
            assertCode(() -> service.submitInPerson(profile.getId(), new MedicalInPersonRequest("VERSENYEZHET", UNTIL, true), actor),
                    "error.auth.forbidden");
        }
        when(medicalRequirement.isRequired(profile.getId())).thenReturn(false);
        assertCode(() -> service.submitInPerson(profile.getId(), new MedicalInPersonRequest("VERSENYEZHET", UNTIL, true), coach),
                "error.medical.not_required");
        verify(certificateRepository, never()).save(any());
    }

    @Test
    void inTheFileModeTheCoachEntersTheDateAndTheFileStaysAskedFor() {
        current(false);
        when(medicalRequirement.fileRequired(profile.getId())).thenReturn(true);

        service.submitInPerson(profile.getId(), new MedicalInPersonRequest("VERSENYEZHET", UNTIL.plusYears(1), true), coach);

        verify(certificateRepository).save(argThat(row -> row.getEvidence() == MedicalCertificate.Evidence.DATE_ONLY
                && !row.hasFile() && row.isVerified() && row.getSubmittedByRole() == MedicalSubmitterRole.COACH));
    }

    @Test
    void aFileIsReplacedInPersonOnlyWhenItIsDueAndTheDateIsLater() {
        MedicalCertificate row = current(true);
        when(medicalRequirement.fileRequired(profile.getId())).thenReturn(true);
        LocalDate today = LocalDate.of(2026, 10, 29);

        assertCode(() -> service.submitInPerson(profile.getId(), new MedicalInPersonRequest("VERSENYEZHET", UNTIL.plusYears(1), true), clubAdmin),
                "error.medical.replace_too_early");
        row.setValidUntil(today.plusDays(31));
        assertCode(() -> service.submitInPerson(profile.getId(), new MedicalInPersonRequest("VERSENYEZHET", UNTIL, true), coach),
                "error.medical.replace_too_early");
        verify(certificateRepository, never()).save(any());

        row.setValidUntil(today.plusDays(30));
        assertCode(() -> service.submitInPerson(profile.getId(), new MedicalInPersonRequest("VERSENYEZHET", today.plusDays(30), true), coach),
                "error.medical.replace_not_later");

        service.submitInPerson(profile.getId(), new MedicalInPersonRequest("VERSENYEZHET", UNTIL, true), coach);
        assertThat(row.getSupersededAt()).isEqualTo(NOW);
        assertThat(row.getObjectKey()).isNull();
    }

    @Test
    void aBanIsRecordedInPersonEvenOverAValidFile() {
        MedicalCertificate row = current(true);
        when(medicalRequirement.fileRequired(profile.getId())).thenReturn(true);

        service.submitInPerson(profile.getId(), new MedicalInPersonRequest("VEGLEGESEN_ELTILTVA", null, true), coach);

        assertThat(row.getSupersededAt()).isEqualTo(NOW);
    }

    @Test
    void theCoachUploadsOnlyWhereTheFileIsAskedForAndUnderTheSameRule() {
        when(medicalAccess.submitterRole(coach, profile)).thenReturn(Optional.of(MedicalSubmitterRole.COACH));
        SubmitMedicalCertificateRequest upload = new SubmitMedicalCertificateRequest("VERSENYEZHET", UNTIL, true, "image/jpeg", 1000L, true);

        assertCode(() -> service.submit(profile.getId(), upload, coach), "error.medical.in_person_only");

        when(medicalRequirement.fileRequired(profile.getId())).thenReturn(true);
        MedicalCertificate row = current(true);
        assertCode(() -> service.submit(profile.getId(), upload, coach), "error.medical.replace_too_early");
        verify(certificateRepository, never()).save(any());

        row.setValidUntil(LocalDate.of(2026, 11, 10));
        service.submit(profile.getId(), upload, coach);
        verify(certificateRepository).save(argThat(saved -> saved.getSubmittedByRole() == MedicalSubmitterRole.COACH && saved.isPending()));
    }

    @Test
    void theParentSeesWhoEnteredItAndWhoVerified() {
        MedicalCertificate row = current(false);
        row.setSubmittedByUserId(coach.getId());
        row.setSubmittedByRole(MedicalSubmitterRole.COACH);
        row.setVerifiedAt(NOW);
        row.setVerifiedByUserId(coach.getId());
        row.setVerifiedByRole(MedicalSubmitterRole.COACH);
        row.setVerificationMethod(MedicalCertificate.VerificationMethod.ORIGINAL);
        coach.setFirstName("Péter");
        coach.setLastName("Kiss");
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(coach));

        var status = service.getStatus(profile.getId(), parent);

        assertThat(status.submittedByRole()).isEqualTo("COACH");
        assertThat(status.submittedByLastName()).isEqualTo("Kiss");
        assertThat(status.verifiedByLastName()).isEqualTo("Kiss");
        assertThat(status.verifiedByRole()).isEqualTo("COACH");
        assertThat(status.verificationMethod()).isEqualTo("ORIGINAL");
    }

    // ---------------------------------------------------------------- coach and the file

    @Test
    void theCoachGetsTheCertificateIdAndOpensTheFileOnlyWithTheClubsFileAccess() {
        MedicalCertificate row = current(true);
        when(certificateRepository.findById(row.getId())).thenReturn(Optional.of(row));

        var narrow = service.getStatus(profile.getId(), coach);
        assertThat(narrow.certificateId()).isNull();
        assertThat(narrow.hasFile()).isNull();
        assertThat(narrow.history()).isNull();
        assertCode(() -> service.openFile(profile.getId(), row.getId(), coach), "error.auth.forbidden");

        when(medicalAccess.isCoachWithFileAccess(coach, profile.getId())).thenReturn(true);
        var open = service.getStatus(profile.getId(), coach);
        assertThat(open.certificateId()).isEqualTo(row.getId());
        assertThat(open.hasFile()).isTrue();
        assertThat(open.coachFileAccess()).isTrue();
        service.openFile(profile.getId(), row.getId(), coach);
        verify(auditEventService).record(eq("MEDICAL_CERTIFICATE_OPENED"), eq(coach.getId()), eq("COACH"), eq("MEDICAL_CERTIFICATE"), any(), any());
    }

    @Test
    void theParentSeesWhetherTheCoachMayOpenTheFileNow() {
        current(true);
        Club club = requiringClub();
        club.setMedicalEvidence(MedicalEvidence.REDACTED_FILE);

        assertThat(service.getStatus(profile.getId(), parent).coachFileAccess()).isFalse();
        club.setMedicalCoachFileAccess(true);
        assertThat(service.getStatus(profile.getId(), parent).coachFileAccess()).isTrue();
    }

    // ---------------------------------------------------------------- club list

    @Test
    void theClubListShowsEveryChildOfItsOnTeamsOnceWithFiltersAndTheTeamsWithoutACoach() {
        Club club = new Club();
        club.setId(UUID.randomUUID());
        club.setMedicalMode(MedicalMode.ON);
        Team files = team(club, "A csapat", MedicalEvidence.REDACTED_FILE);
        Team dates = team(club, "B csapat", MedicalEvidence.INHERIT);
        Team off = team(club, "C csapat", MedicalEvidence.INHERIT);
        off.setMedicalMode(MedicalMode.OFF);
        // Counted from coach_teams: the entity set stays empty for an extra-hat coach.
        when(teamMembershipQuery.countCoachesByTeamIds(anyCollection())).thenReturn(Map.of(files.getId(), 1L));
        PlayerProfile second = new PlayerProfile();
        second.setId(UUID.randomUUID());
        second.setFirstName("Béla");
        second.setLastName("Nagy");
        profile.setFirstName("Anna");
        profile.setLastName("Kiss");
        when(teamRepository.findByClub_Id(club.getId())).thenReturn(List.of(files, dates, off));
        when(teamRosterRepository.findProfileIdsByTeamId(files.getId())).thenReturn(List.of(profile.getId()));
        when(teamRosterRepository.findProfileIdsByTeamId(dates.getId())).thenReturn(List.of(profile.getId(), second.getId()));
        when(playerProfileRepository.findAllById(anyCollection())).thenReturn(List.of(profile, second));
        MedicalCertificate row = current(true);
        when(certificateRepository.findCurrentByProfileIdIn(anyCollection())).thenReturn(List.of(row));
        when(medicalRequirement.fileRequiredProfileIds(anyCollection())).thenReturn(Set.of(profile.getId()));

        var list = service.clubStatus(club.getId(), null, null, null, null, clubAdmin);

        verify(userAccessService).requireOwnClubAdmin(clubAdmin, club.getId());
        assertThat(list.page().items()).extracting(item -> item.lastName()).containsExactly("Kiss", "Nagy");
        var anna = list.page().items().get(0);
        assertThat(anna.teamIds()).containsExactly(files.getId(), dates.getId());
        assertThat(anna.status()).isEqualTo("UNVERIFIED");
        assertThat(anna.hasFile()).isTrue();
        assertThat(anna.fileRequired()).isTrue();
        var bela = list.page().items().get(1);
        assertThat(bela.status()).isEqualTo("MISSING");
        assertThat(bela.hasFile()).isNull();
        assertThat(list.teamsWithoutCoach()).extracting(ClubMedicalStatusResponse.TeamRef::name).containsExactly("B csapat");
        assertThat(list.counts()).containsEntry("UNVERIFIED", 1).containsEntry("MISSING", 1).containsEntry("BANNED", 0);

        var waiting = service.clubStatus(club.getId(), "unverified", null, 1, 20, clubAdmin);
        assertThat(waiting.page().items()).extracting(item -> item.lastName()).containsExactly("Kiss");
        assertThat(waiting.page().total()).isEqualTo(1);
        assertThatThrownBy(() -> service.clubStatus(club.getId(), "LATE", null, null, null, clubAdmin)).isInstanceOf(BadRequestException.class);

        // One team's filter narrows the list and the counts, and the child still shows every team it plays in.
        var filesOnly = service.clubStatus(club.getId(), null, files.getId(), null, null, clubAdmin);
        assertThat(filesOnly.page().items()).extracting(item -> item.lastName()).containsExactly("Kiss");
        assertThat(filesOnly.page().items().get(0).teamIds()).containsExactly(files.getId(), dates.getId());
        assertThat(filesOnly.counts()).containsEntry("UNVERIFIED", 1).containsEntry("MISSING", 0);
        assertThat(filesOnly.teams()).hasSize(2);
        assertThat(service.clubStatus(club.getId(), null, off.getId(), null, null, clubAdmin).page().items()).isEmpty();
        assertThat(service.clubStatus(club.getId(), null, UUID.randomUUID(), null, null, clubAdmin).page().items()).isEmpty();
    }

    @Test
    void theNotCompetingFilterCountsVerifiedOrNegativeRowsThatDoNotAllowCompeting() {
        assertThat(MedicalCertificateService.matches(item("VALID", false), "NOT_COMPETING")).isTrue();
        assertThat(MedicalCertificateService.matches(item("VALID", true), "NOT_COMPETING")).isFalse();
        assertThat(MedicalCertificateService.matches(item("UNVERIFIED", false), "NOT_COMPETING")).isFalse();
        assertThat(MedicalCertificateService.matches(item("SUSPENDED", false), "NOT_COMPETING")).isTrue();
        assertThat(MedicalCertificateService.matches(item("EXPIRED", false), "EXPIRING")).isTrue();
        assertThat(MedicalCertificateService.matches(item("NOT_FIT", false), "BANNED")).isTrue();
    }

    // ---------------------------------------------------------------- helpers

    private static ClubMedicalStatusItem item(String status, boolean competing) {
        return new ClubMedicalStatusItem(UUID.randomUUID(), "A", "B", List.of(), status,
                null, null, competing, null, null, false, null, null, false);
    }

    private static Team team(Club club, String name, MedicalEvidence evidence) {
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setName(name);
        team.setClub(club);
        team.setMedicalEvidence(evidence);
        team.setCoaches(new HashSet<>());
        return team;
    }

    private Club requiringClub() {
        Club club = new Club();
        club.setId(UUID.randomUUID());
        club.setName("Kék FC");
        club.setMedicalMode(MedicalMode.ON);
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setClub(club);
        when(medicalRequirement.requiringTeams(profile.getId())).thenReturn(List.of(team));
        return club;
    }

    /** The profile's current row, unverified, VERSENYEZHET until UNTIL; with or without a stored file. */
    private MedicalCertificate current(boolean withFile) {
        MedicalCertificate row = new MedicalCertificate();
        row.setId(UUID.randomUUID());
        row.setPlayerProfileId(profile.getId());
        row.setResult(MedicalResult.VERSENYEZHET);
        row.setValidUntil(UNTIL);
        row.setEvidence(withFile ? MedicalCertificate.Evidence.FILE : MedicalCertificate.Evidence.DATE_ONLY);
        if (withFile) {
            row.setObjectKey("medical/" + profile.getId() + "/" + row.getId());
            row.setContentType("application/pdf");
            row.setSizeBytes(1000L);
        }
        row.setSubmittedByRole(MedicalSubmitterRole.PARENT);
        row.setAcknowledgementVersion(withFile ? MedicalCertificate.ACKNOWLEDGEMENT_FILE : MedicalCertificate.ACKNOWLEDGEMENT_DATE);
        row.setCreatedAt(NOW.minusSeconds(86_400));
        row.setCompletedAt(NOW.minusSeconds(86_000));
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(row));
        return row;
    }

    private static void assertCode(ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOfAny(BadRequestException.class, ConflictException.class, ForbiddenException.class, NotFoundException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getMessageCode()).isEqualTo(code));
    }
}
