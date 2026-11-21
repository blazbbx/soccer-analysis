package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.AppException;
import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.ClubMedicalAgreement;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.repository.ClubMedicalAgreementRepository;
import com.example.footballanalysis.repository.ClubRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The operator making sport medical collection available to a club with its signed agreement
 * (docs/sportorvosi-jogszeruseg.md, Engedélyezés az üzemeltetőtől).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MedicalAgreementServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private static final byte[] PDF = "%PDF-1.7 signed".getBytes();

    @Mock private ClubRepository clubRepository;
    @Mock private ClubMedicalAgreementRepository agreementRepository;
    @Mock private ClubDocumentStore documentStore;
    @Mock private UserAccessService userAccessService;
    @Mock private AuditEventService auditEventService;

    private Club club;
    private User platformAdmin;
    private User clubAdmin;

    @BeforeEach
    void setUp() {
        club = new Club();
        club.setId(UUID.randomUUID());
        club.setName("Kék FC");
        platformAdmin = user(UserRole.ADMIN);
        clubAdmin = user(UserRole.CLUB_ADMIN);
        when(clubRepository.findById(club.getId())).thenReturn(Optional.of(club));
        when(clubRepository.findByIdForUpdate(club.getId())).thenReturn(Optional.of(club));
        when(userAccessService.isAdmin(platformAdmin)).thenReturn(true);
        when(agreementRepository.findFirstByClubIdAndEndedAtIsNull(club.getId())).thenReturn(Optional.empty());
        when(documentStore.downloadUrl(anyString(), anyString())).thenReturn("http://minio/agreement");
    }

    @Test
    void thePlatformAdminRecordsTheSignedAgreementAndCollectionBecomesAvailable() {
        var response = service("v1").record(club.getId(), PDF, platformAdmin);

        // The club is locked first, so a second recording at the same time waits and then replaces this one.
        verify(clubRepository).findByIdForUpdate(club.getId());
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(documentStore).putPdf(key.capture(), eq(PDF));
        assertThat(key.getValue()).startsWith("agreements/" + club.getId() + "/").endsWith(".pdf");
        ArgumentCaptor<ClubMedicalAgreement> saved = ArgumentCaptor.forClass(ClubMedicalAgreement.class);
        verify(agreementRepository).save(saved.capture());
        ClubMedicalAgreement agreement = saved.getValue();
        assertThat(agreement.getClubId()).isEqualTo(club.getId());
        assertThat(agreement.getClubName()).isEqualTo("Kék FC");
        assertThat(agreement.getDpaVersion()).isEqualTo("v1");
        assertThat(agreement.getObjectKey()).isEqualTo(key.getValue());
        assertThat(agreement.getRecordedAt()).isEqualTo(NOW);
        assertThat(agreement.getRecordedByUserId()).isEqualTo(platformAdmin.getId());
        assertThat(club.getMedicalAgreementVersion()).isEqualTo("v1");
        assertThat(response.active()).isTrue();
        verify(auditEventService).record(eq("MEDICAL_AGREEMENT_RECORDED"), eq(platformAdmin.getId()), eq("CLUB"),
                eq(club.getId().toString()), eq("version=v1,agreementId=" + agreement.getId()));
    }

    @Test
    void aNewAgreementReplacesTheLiveOneAndKeepsItsFile() {
        ClubMedicalAgreement previous = agreement("v1");
        when(agreementRepository.findFirstByClubIdAndEndedAtIsNull(club.getId())).thenReturn(Optional.of(previous));

        service("v2").record(club.getId(), PDF, platformAdmin);

        assertThat(previous.getEndedAt()).isEqualTo(NOW);
        assertThat(previous.getEndKind()).isEqualTo(ClubMedicalAgreement.EndKind.REPLACED);
        assertThat(previous.getEndedByUserId()).isEqualTo(platformAdmin.getId());
        assertThat(previous.getWithdrawalReason()).isNull();
        verify(agreementRepository).saveAndFlush(previous);
        verify(documentStore, never()).delete(any());
        verify(documentStore, never()).deleteAfterCommit(any());
        assertThat(club.getMedicalAgreementVersion()).isEqualTo("v2");
    }

    @Test
    void onlyThePlatformAdminRecordsAndWithdraws() {
        MedicalAgreementService service = service("v1");

        assertThatThrownBy(() -> service.record(club.getId(), PDF, clubAdmin))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.withdraw(club.getId(), "The club ended the contract.", clubAdmin))
                .isInstanceOf(ForbiddenException.class);
        verify(documentStore, never()).putPdf(anyString(), any());
        assertThat(club.getMedicalAgreementVersion()).isNull();
    }

    @Test
    void nothingCanBeRecordedBeforeTheApprovedAgreementExists() {
        assertCode(() -> service("").record(club.getId(), PDF, platformAdmin),
                "error.medical.dpa_unavailable");
        verify(documentStore, never()).putPdf(anyString(), any());
    }

    @Test
    void onlyAPdfOfAtMostFiveMebibytesIsAccepted() {
        MedicalAgreementService service = service("v1");

        assertCode(() -> service.record(club.getId(), "GIF89a".getBytes(), platformAdmin), "error.medical.agreement_file_type");
        assertCode(() -> service.record(club.getId(), new byte[0], platformAdmin), "error.medical.agreement_file_type");
        assertCode(() -> service.record(club.getId(), null, platformAdmin), "error.medical.agreement_file_type");
        byte[] big = Arrays.copyOf(PDF, (int) MedicalAgreementService.FILE_MAX_BYTES + 1);
        assertCode(() -> service.record(club.getId(), big, platformAdmin), "error.medical.agreement_too_large");
        verify(documentStore, never()).putPdf(anyString(), any());

        byte[] exact = Arrays.copyOf(PDF, (int) MedicalAgreementService.FILE_MAX_BYTES);
        service.record(club.getId(), exact, platformAdmin);
        assertThat(club.getMedicalAgreementVersion()).isEqualTo("v1");
    }

    @Test
    void withdrawingEndsTheLiveAgreementWithTheReasonAndStopsAvailability() {
        ClubMedicalAgreement live = agreement("v1");
        club.setMedicalAgreementVersion("v1");
        when(agreementRepository.findFirstByClubIdAndEndedAtIsNull(club.getId())).thenReturn(Optional.of(live));
        MedicalAgreementService service = service("v1");

        assertCode(() -> service.withdraw(club.getId(), "too short", platformAdmin), "error.medical.agreement_reason");
        service.withdraw(club.getId(), "  The club ended the contract.  ", platformAdmin);

        assertThat(live.getEndKind()).isEqualTo(ClubMedicalAgreement.EndKind.WITHDRAWN);
        assertThat(live.getWithdrawalReason()).isEqualTo("The club ended the contract.");
        assertThat(live.getEndedAt()).isEqualTo(NOW);
        assertThat(club.getMedicalAgreementVersion()).isNull();
        verify(auditEventService).record(eq("MEDICAL_AGREEMENT_WITHDRAWN"), eq(platformAdmin.getId()), eq("CLUB"),
                eq(club.getId().toString()), eq("version=v1,agreementId=" + live.getId()));
        verify(documentStore, never()).deleteAfterCommit(any());
    }

    @Test
    void withdrawingWithoutALiveAgreementIsNotFound() {
        assertThatThrownBy(() -> service("v1").withdraw(club.getId(), "The club ended the contract.", platformAdmin))
                .isInstanceOf(NotFoundException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getMessageCode()).isEqualTo("error.medical.agreement_not_found"));
    }

    @Test
    void theClubsAdminsAndThePlatformAdminReadTheListAndTheFileOfThisClubOnly() {
        ClubMedicalAgreement live = agreement("v1");
        when(agreementRepository.findByClubIdOrderByRecordedAtDescIdDesc(club.getId())).thenReturn(List.of(live));
        when(agreementRepository.findByIdAndClubId(live.getId(), club.getId())).thenReturn(Optional.of(live));
        MedicalAgreementService service = service("v1");

        assertThat(service.list(club.getId(), clubAdmin)).singleElement()
                .satisfies(item -> assertThat(item.active()).isTrue());
        var file = service.file(club.getId(), live.getId(), clubAdmin);
        assertThat(file.url()).isEqualTo("http://minio/agreement");
        verify(documentStore).downloadUrl(live.getObjectKey(), "adatfeldolgozasi-szerzodes.pdf");
        verify(userAccessService, org.mockito.Mockito.times(2)).requireClubAdmin(clubAdmin, club.getId());

        // Another club's agreement id is not found under this club.
        assertThatThrownBy(() -> service.file(club.getId(), UUID.randomUUID(), clubAdmin)).isInstanceOf(NotFoundException.class);

        User stranger = user(UserRole.CLUB_ADMIN);
        doThrow(new ForbiddenException("error.auth.forbidden", new Object[0], "no"))
                .when(userAccessService).requireClubAdmin(stranger, club.getId());
        assertThatThrownBy(() -> service.list(club.getId(), stranger)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.file(club.getId(), live.getId(), stranger)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void theAgreementCountsOnlyForTheCurrentVersion() {
        MedicalCompliance compliance = new MedicalCompliance("v2", "https://legal.example/dpa.pdf", "");
        assertThat(compliance.hasAgreement(club)).isFalse();
        club.setMedicalAgreementVersion("v1");
        assertThat(compliance.hasAgreement(club)).isFalse();
        club.setMedicalAgreementVersion("v2");
        assertThat(compliance.hasAgreement(club)).isTrue();
        assertThat(new MedicalCompliance("", "", "").hasAgreement(club)).isFalse();
    }

    private ClubMedicalAgreement agreement(String version) {
        ClubMedicalAgreement agreement = new ClubMedicalAgreement();
        agreement.setId(UUID.randomUUID());
        agreement.setClubId(club.getId());
        agreement.setClubName(club.getName());
        agreement.setDpaVersion(version);
        agreement.setObjectKey("agreements/" + club.getId() + "/old.pdf");
        agreement.setRecordedAt(NOW.minusSeconds(86_400));
        return agreement;
    }

    private MedicalAgreementService service(String dpaVersion) {
        return new MedicalAgreementService(clubRepository, agreementRepository, documentStore,
                new MedicalCompliance(dpaVersion, dpaVersion.isEmpty() ? "" : "https://legal.example/dpa.pdf", ""),
                userAccessService, auditEventService, Clock.fixed(NOW, ZoneId.of("Europe/Budapest")));
    }

    private static User user(UserRole role) {
        User user = TestHats.user(role);
        user.setId(UUID.randomUUID());
        TestHats.grant(user, role);
        return user;
    }

    private static void assertCode(Runnable call, String code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getMessageCode()).isEqualTo(code));
    }
}
