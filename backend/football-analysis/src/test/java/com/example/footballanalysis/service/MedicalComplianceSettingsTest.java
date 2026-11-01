package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.AppException;
import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ConflictWithPropertiesException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.MedicalMode;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.user.ClubAdmin;
import com.example.footballanalysis.model.db.user.Fan;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.UpdateClubMedicalSettingsRequest;
import com.example.footballanalysis.model.requests.UpdateTeamMedicalModeRequest;
import com.example.footballanalysis.repository.ClubRepository;
import com.example.footballanalysis.repository.MedicalCertificateRepository;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.TeamRosterRepository;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The conditions of turning collection on (docs/sportorvosi-jogszeruseg.md, Bekapcsolási feltételek, PR 9):
 * the legal basis, the privacy notice and the accepted processing agreement, with the real {@link MedicalCompliance}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MedicalComplianceSettingsTest {

    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");
    private static final String LAW = "2004. évi I. törvény 2. § és a 2004. évi sportorvosi rendelet";
    private static final byte[] PDF = "%PDF-1.7 notice".getBytes();

    @Mock private ClubRepository clubRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private TeamRosterRepository teamRosterRepository;
    @Mock private MedicalCertificateRepository certificateRepository;
    @Mock private MedicalRequirementService medicalRequirement;
    @Mock private MedicalCertificateService medicalCertificateService;
    @Mock private MedicalFileStore fileStore;
    @Mock private ClubDocumentStore documentStore;
    @Mock private MedicalStorageGuard storageGuard;
    @Mock private UserAccessService userAccessService;
    @Mock private AuditEventService auditEventService;

    private Club club;
    private ClubAdmin admin;

    @BeforeEach
    void setUp() {
        club = new Club();
        club.setId(UUID.randomUUID());
        club.setName("Kék FC");
        admin = new ClubAdmin();
        admin.setId(UUID.randomUUID());
        admin.setRole(UserRole.CLUB_ADMIN);
        when(clubRepository.findById(club.getId())).thenReturn(Optional.of(club));
        when(teamRosterRepository.findProfileIdsByClubId(club.getId())).thenReturn(List.of());
        when(medicalRequirement.requiredProfileIds(anyCollection())).thenReturn(Set.of());
        when(storageGuard.lastResult()).thenReturn(new MedicalStorageGuard.Result(true, List.of()));
        when(documentStore.downloadUrl(anyString())).thenReturn("http://minio/notice");
    }

    @Test
    void aNewClubMissesAllThreeAndCannotBeTurnedOn() {
        MedicalSettingsService service = service("v1");

        var settings = service.getClubSettings(club.getId(), admin);
        assertThat(settings.ready()).isFalse();
        assertThat(settings.missing()).containsExactly("legalBasis", "notice", "dpa");

        assertThatThrownBy(() -> service.updateClubSettings(club.getId(), request("ON", true, null, null, null), admin))
                .isInstanceOf(ConflictWithPropertiesException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getMessageCode()).isEqualTo("error.medical.club_not_ready");
                    assertThat(((ConflictWithPropertiesException) ex).getProperties())
                            .containsEntry("missing", List.of("legalBasis", "notice", "dpa"));
                });
        assertThat(club.getMedicalMode()).isEqualTo(MedicalMode.OFF);
        verify(clubRepository, never()).save(any());
    }

    @Test
    void oneRequestCanCompleteTheConditionsAndTurnCollectionOn() {
        MedicalSettingsService service = service("v1");

        var settings = service.updateClubSettings(club.getId(),
                request("ON", true, LAW, "https://kekfc.hu/adatkezeles", "v1"), admin);

        assertThat(settings.mode()).isEqualTo("ON");
        assertThat(settings.ready()).isTrue();
        assertThat(settings.notice().kind()).isEqualTo("URL");
        assertThat(settings.notice().version()).isEqualTo(1);
        assertThat(club.getMedicalLegalBasis()).isEqualTo(LAW);
        assertThat(club.getMedicalDpaVersion()).isEqualTo("v1");
        assertThat(club.getMedicalDpaAcceptedAt()).isEqualTo(NOW);
        assertThat(club.getMedicalDpaAcceptedByUserId()).isEqualTo(admin.getId());
        verify(auditEventService).record(eq("MEDICAL_DPA_ACCEPTED"), any(), any(), eq("CLUB"), eq(club.getId().toString()), eq("version=v1"));
        verify(auditEventService).record(eq("MEDICAL_NOTICE_SET"), any(), any(), eq("CLUB"), eq(club.getId().toString()), eq("kind=URL,version=1"));
    }

    @Test
    void anOldAgreementVersionIsMissingAndTheWrongVersionCannotBeAccepted() {
        MedicalSettingsService service = service("v2");
        club.setMedicalLegalBasis(LAW);
        club.setMedicalNoticeUrl("https://kekfc.hu/adatkezeles");
        club.setMedicalDpaVersion("v1");

        assertThat(service.getClubSettings(club.getId(), admin).missing()).containsExactly("dpa");
        assertCode(() -> service.updateClubSettings(club.getId(), request(null, null, null, null, "v1"), admin),
                "error.medical.dpa_version");
        service.updateClubSettings(club.getId(), request(null, null, null, null, "v2"), admin);
        assertThat(service.getClubSettings(club.getId(), admin).ready()).isTrue();
    }

    @Test
    void withoutAConfiguredAgreementNothingCanBeAcceptedAndNoClubIsReady() {
        MedicalSettingsService service = service("");
        club.setMedicalLegalBasis(LAW);
        club.setMedicalNoticeUrl("https://kekfc.hu/adatkezeles");

        var settings = service.getClubSettings(club.getId(), admin);
        assertThat(settings.ready()).isFalse();
        assertThat(settings.missing()).containsExactly("dpa");
        assertThat(settings.currentDpaVersion()).isNull();
        assertThat(settings.dpaDocumentUrl()).isNull();
        assertThat(settings.noticeTemplateUrl()).isNull();
        assertCode(() -> service.updateClubSettings(club.getId(), request(null, null, null, null, "v1"), admin),
                "error.medical.dpa_unavailable");
    }

    @Test
    void theLegalBasisAndTheLinkAreValidated() {
        MedicalSettingsService service = service("v1");

        assertCode(() -> service.updateClubSettings(club.getId(), request(null, null, "too short", null, null), admin),
                "error.medical.legal_basis");
        assertCode(() -> service.updateClubSettings(club.getId(), request(null, null, "x".repeat(501), null, null), admin),
                "error.medical.legal_basis");
        assertCode(() -> service.updateClubSettings(club.getId(), request(null, null, null, "http://kekfc.hu/a", null), admin),
                "error.medical.notice_url");
        assertCode(() -> service.updateClubSettings(club.getId(), request(null, null, null, "https://", null), admin),
                "error.medical.notice_url");
    }

    @Test
    void aTeamTurnedOnNeedsAReadyClubEvenWhenTheClubIsOff() {
        MedicalSettingsService service = service("v1");
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setClub(club);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> service.updateTeamMode(team.getId(), new UpdateTeamMedicalModeRequest("ON", true, null, null), admin))
                .isInstanceOf(ConflictWithPropertiesException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getMessageCode()).isEqualTo("error.medical.club_not_ready"));
        assertThat(team.getMedicalMode()).isEqualTo(MedicalMode.INHERIT);

        makeReady();
        service.updateTeamMode(team.getId(), new UpdateTeamMedicalModeRequest("ON", true, null, null), admin);
        assertThat(team.getMedicalMode()).isEqualTo(MedicalMode.ON);
    }

    @Test
    void switchingOffDoesNotNeedAReadyClub() {
        MedicalSettingsService service = service("v2");
        club.setMedicalMode(MedicalMode.ON);

        service.updateClubSettings(club.getId(), request("OFF", null, null, null, null), admin);

        assertThat(club.getMedicalMode()).isEqualTo(MedicalMode.OFF);
    }

    @Test
    void anUploadedPdfReplacesTheLinkRaisesTheVersionAndDeletesTheOldFileAfterCommit() {
        MedicalSettingsService service = service("v1");
        club.setMedicalNoticeObjectKey("notices/old.pdf");
        club.setMedicalNoticeVersion(2);

        var settings = service.uploadNotice(club.getId(), PDF, admin);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(documentStore).putPdf(key.capture(), eq(PDF));
        assertThat(key.getValue()).startsWith("notices/" + club.getId() + "/").endsWith(".pdf");
        assertThat(club.getMedicalNoticeObjectKey()).isEqualTo(key.getValue());
        assertThat(club.getMedicalNoticeUrl()).isNull();
        assertThat(settings.notice().kind()).isEqualTo("FILE");
        assertThat(settings.notice().version()).isEqualTo(3);
        assertThat(settings.notice().updatedAt()).isEqualTo(NOW);
        verify(documentStore).deleteAfterCommit("notices/old.pdf");
        verify(auditEventService).record(eq("MEDICAL_NOTICE_SET"), any(), any(), eq("CLUB"), any(), eq("kind=FILE,version=3"));
    }

    @Test
    void aNoticeMustBeAPdfOfAtMostFiveMebibytes() {
        MedicalSettingsService service = service("v1");

        assertCode(() -> service.uploadNotice(club.getId(), "GIF89a".getBytes(), admin), "error.medical.notice_file_type");
        assertCode(() -> service.uploadNotice(club.getId(), new byte[0], admin), "error.medical.notice_file_type");
        assertCode(() -> service.uploadNotice(club.getId(), null, admin), "error.medical.notice_file_type");
        byte[] big = new byte[(int) MedicalSettingsService.NOTICE_MAX_BYTES + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        assertCode(() -> service.uploadNotice(club.getId(), big, admin), "error.medical.notice_too_large");
        verify(documentStore, never()).putPdf(anyString(), any());

        byte[] exact = Arrays.copyOf(PDF, (int) MedicalSettingsService.NOTICE_MAX_BYTES);
        service.uploadNotice(club.getId(), exact, admin);
        verify(documentStore).putPdf(anyString(), eq(exact));
    }

    @Test
    void theNoticeCannotBeRemovedWhileATeamCollectsButCanBeReplaced() {
        MedicalSettingsService service = service("v1");
        club.setMedicalNoticeObjectKey("notices/old.pdf");
        Team team = new Team();
        team.setClub(club);
        team.setMedicalMode(MedicalMode.ON);
        when(teamRepository.findByClub_Id(club.getId())).thenReturn(List.of(team));

        assertThatThrownBy(() -> service.removeNotice(club.getId(), admin))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getMessageCode()).isEqualTo("error.medical.notice_in_use"));
        assertThat(club.getMedicalNoticeObjectKey()).isEqualTo("notices/old.pdf");

        service.uploadNotice(club.getId(), PDF, admin);
        assertThat(club.getMedicalNoticeObjectKey()).isNotEqualTo("notices/old.pdf");

        team.setMedicalMode(MedicalMode.OFF);
        String current = club.getMedicalNoticeObjectKey();
        service.removeNotice(club.getId(), admin);
        assertThat(MedicalCompliance.hasNotice(club)).isFalse();
        verify(documentStore).deleteAfterCommit(current);
        verify(auditEventService).record(eq("MEDICAL_NOTICE_REMOVED"), any(), any(), eq("CLUB"), any(), eq("kind=FILE,version=1"));
    }

    @Test
    void anySignedInUserReadsTheNoticeAsALinkOrAPresignedPdf() {
        MedicalSettingsService service = service("v1");
        Fan fan = new Fan();
        fan.setId(UUID.randomUUID());
        fan.setRole(UserRole.FAN);

        assertThatThrownBy(() -> service.getNotice(club.getId(), fan)).isInstanceOf(NotFoundException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getMessageCode()).isEqualTo("error.medical.notice_not_found"));

        club.setMedicalNoticeUrl("https://kekfc.hu/adatkezeles");
        assertThat(service.getNotice(club.getId(), fan).url()).isEqualTo("https://kekfc.hu/adatkezeles");

        club.setMedicalNoticeUrl(null);
        club.setMedicalNoticeObjectKey("notices/a.pdf");
        var file = service.getNotice(club.getId(), fan);
        assertThat(file.kind()).isEqualTo("FILE");
        assertThat(file.url()).isEqualTo("http://minio/notice");
        assertThat(file.expiresInSeconds()).isEqualTo(600);
        verify(userAccessService, never()).requireOwnClubAdmin(any(), any());
    }

    private void makeReady() {
        club.setMedicalLegalBasis(LAW);
        club.setMedicalNoticeUrl("https://kekfc.hu/adatkezeles");
        club.setMedicalDpaVersion("v1");
    }

    private MedicalSettingsService service(String dpaVersion) {
        return new MedicalSettingsService(clubRepository, teamRepository, teamRosterRepository, certificateRepository,
                medicalRequirement, medicalCertificateService, fileStore, documentStore, storageGuard,
                new MedicalCompliance(dpaVersion, dpaVersion.isEmpty() ? "" : "https://legal.example/dpa.pdf", ""),
                userAccessService, auditEventService, Clock.fixed(NOW, ZoneId.of("Europe/Budapest")));
    }

    private static UpdateClubMedicalSettingsRequest request(String mode, Boolean acknowledged, String legalBasis,
                                                            String noticeUrl, String dpa) {
        return new UpdateClubMedicalSettingsRequest(mode, acknowledged, null, null, legalBasis, noticeUrl, dpa, null, null, null);
    }

    private static void assertCode(Runnable call, String code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getMessageCode()).isEqualTo(code));
    }
}
