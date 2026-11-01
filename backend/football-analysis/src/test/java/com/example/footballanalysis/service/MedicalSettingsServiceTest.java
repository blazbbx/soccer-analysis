package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.AppException;
import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictWithPropertiesException;
import com.example.footballanalysis.exception.ExternalServiceException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.MedicalCertificate;
import com.example.footballanalysis.model.db.MedicalEvidence;
import com.example.footballanalysis.model.db.MedicalMode;
import com.example.footballanalysis.model.db.MedicalSubmitterRole;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.user.Admin;
import com.example.footballanalysis.model.db.user.ClubAdmin;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MedicalSettingsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    @Mock private ClubRepository clubRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private TeamRosterRepository teamRosterRepository;
    @Mock private MedicalCertificateRepository certificateRepository;
    @Mock private MedicalRequirementService medicalRequirement;
    @Mock private MedicalCertificateService medicalCertificateService;
    @Mock private MedicalFileStore fileStore;
    @Mock private MedicalStorageGuard storageGuard;
    @Mock private ClubDocumentStore documentStore;
    @Mock private MedicalCompliance compliance;
    @Mock private UserAccessService userAccessService;
    @Mock private AuditEventService auditEventService;

    private MedicalSettingsService service;
    private Club club;
    private ClubAdmin admin;
    private final UUID onlyHere = UUID.randomUUID();
    private final UUID alsoElsewhere = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MedicalSettingsService(clubRepository, teamRepository, teamRosterRepository, certificateRepository,
                medicalRequirement, medicalCertificateService, fileStore, documentStore, storageGuard, compliance, userAccessService, auditEventService,
                Clock.fixed(NOW, ZoneId.of("Europe/Budapest")));
        club = new Club();
        club.setId(UUID.randomUUID());
        admin = new ClubAdmin();
        admin.setId(UUID.randomUUID());
        admin.setRole(UserRole.CLUB_ADMIN);
        when(clubRepository.findById(club.getId())).thenReturn(Optional.of(club));
        when(storageGuard.lastResult()).thenReturn(new MedicalStorageGuard.Result(true, List.of()));
        when(compliance.missing(any())).thenReturn(List.of());
        when(teamRosterRepository.findProfileIdsByClubId(club.getId())).thenReturn(List.of(onlyHere, alsoElsewhere));
    }

    @Test
    void aNewClubIsOffAndCannotBeTurnedOnWithoutTheAcknowledgement() {
        assertThat(club.getMedicalMode()).isEqualTo(MedicalMode.OFF);

        assertThatThrownBy(() -> service.updateClubSettings(club.getId(),
                clubRequest("ON", false, null, null), admin))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getMessageCode()).isEqualTo("error.medical.acknowledgement_required"));
        assertThat(club.getMedicalMode()).isEqualTo(MedicalMode.OFF);
    }

    @Test
    void turningOnRecordsWhoAcknowledgedAndWhen() {
        when(medicalRequirement.requiredProfileIds(anyCollection())).thenReturn(Set.of());

        var response = service.updateClubSettings(club.getId(), clubRequest("ON", true, null, 5), admin);

        assertThat(response.mode()).isEqualTo("ON");
        assertThat(club.getMedicalAcknowledgedAt()).isEqualTo(NOW);
        assertThat(club.getMedicalAcknowledgedByUserId()).isEqualTo(admin.getId());
        assertThat(club.getMedicalHistoryRetentionYears()).isEqualTo(5);
        verify(medicalRequirement).recalculate(eq(List.of(onlyHere, alsoElsewhere)), any());
    }

    @Test
    void turningOffWithoutConfirmationCountsOnlyChildrenWhoseLastOnTeamThisWas() {
        club.setMedicalMode(MedicalMode.ON);
        MedicalCertificate file = certificate(onlyHere);
        when(medicalRequirement.requiredProfileIds(anyCollection())).thenReturn(Set.of(onlyHere, alsoElsewhere));
        when(medicalRequirement.requiredProfileIdsNow(anyCollection())).thenReturn(Set.of(alsoElsewhere));
        when(certificateRepository.findCurrentByProfileIdIn(List.of(onlyHere))).thenReturn(List.of(file));

        assertThatThrownBy(() -> service.updateClubSettings(club.getId(),
                clubRequest("OFF", null, false, null), admin))
                .isInstanceOf(ConflictWithPropertiesException.class)
                .satisfies(ex -> assertThat(((ConflictWithPropertiesException) ex).getProperties()).containsEntry("affectedProfiles", 1));
        verify(certificateRepository, never()).save(any());
    }

    @Test
    void confirmedTurnOffDeletesOnlyTheDroppedChildsFile() {
        club.setMedicalMode(MedicalMode.ON);
        MedicalCertificate file = certificate(onlyHere);
        String key = file.getObjectKey();
        when(medicalRequirement.requiredProfileIds(anyCollection())).thenReturn(Set.of(onlyHere, alsoElsewhere));
        when(medicalRequirement.requiredProfileIdsNow(anyCollection())).thenReturn(Set.of(alsoElsewhere));
        when(certificateRepository.findCurrentByProfileIdIn(List.of(onlyHere))).thenReturn(List.of(file));

        service.updateClubSettings(club.getId(), clubRequest("OFF", null, true, null), admin);

        assertThat(file.getWithdrawnAt()).isEqualTo(NOW);
        assertThat(file.getObjectKey()).isNull();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<MedicalFileStore.KeyRef>> keys = ArgumentCaptor.forClass(Collection.class);
        verify(fileStore).deleteAfterCommit(keys.capture());
        assertThat(keys.getValue()).containsExactly(new MedicalFileStore.KeyRef(key, file.getId()));
        verify(certificateRepository, never()).findCurrentByProfileIdIn(List.of(alsoElsewhere));
    }

    @Test
    void theAfterCheckRunsOnLockedProfilesSoAnotherClubSwitchedOffMeanwhileCounts() {
        // The other club was switched off by a transaction that committed while this one waited for the lock:
        // the fresh check after the lock no longer sees it, so the child that was in both clubs loses the file too.
        club.setMedicalMode(MedicalMode.ON);
        MedicalCertificate here = certificate(onlyHere);
        MedicalCertificate both = certificate(alsoElsewhere);
        when(medicalRequirement.requiredProfileIds(anyCollection())).thenReturn(Set.of(onlyHere, alsoElsewhere));
        when(medicalRequirement.requiredProfileIdsNow(anyCollection())).thenReturn(Set.of());
        when(certificateRepository.findCurrentByProfileIdIn(anyCollection())).thenReturn(List.of(here, both));

        service.updateClubSettings(club.getId(), clubRequest("OFF", null, true, null), admin);

        InOrder order = inOrder(medicalRequirement);
        // The whole roster in one go, the same set recalculate locks, so two switches cannot deadlock.
        order.verify(medicalRequirement).lockProfiles(List.of(onlyHere, alsoElsewhere));
        order.verify(medicalRequirement).requiredProfileIdsNow(Set.of(onlyHere, alsoElsewhere));
        order.verify(medicalRequirement).recalculate(eq(List.of(onlyHere, alsoElsewhere)), any());
        assertThat(here.getObjectKey()).isNull();
        assertThat(both.getObjectKey()).isNull();
    }

    @Test
    void onlyTheClubsOwnAdminsWriteTheSettingsButThePlatformAdminCanRead() {
        Admin operator = new Admin();
        operator.setId(UUID.randomUUID());
        operator.setRole(UserRole.ADMIN);
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setClub(club);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        doThrow(new ForbiddenException("error.auth.forbidden", new Object[0], "Only the club's own admins can do this."))
                .when(userAccessService).requireOwnClubAdmin(operator, club.getId());

        assertThatThrownBy(() -> service.updateClubSettings(club.getId(), clubRequest("ON", true, null, null), operator))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.updateTeamMode(team.getId(), new UpdateTeamMedicalModeRequest("ON", true, null, null), operator))
                .isInstanceOf(ForbiddenException.class);
        assertThat(club.getMedicalMode()).isEqualTo(MedicalMode.OFF);
        assertThat(team.getMedicalMode()).isEqualTo(MedicalMode.INHERIT);
        verify(clubRepository, never()).save(any());
        verify(teamRepository, never()).save(any());

        assertThat(service.getClubSettings(club.getId(), operator).mode()).isEqualTo("OFF");
        verify(userAccessService).requireClubAdmin(operator, club.getId());
    }

    @Test
    void switchingOffHandsTheClubsThatRequiredTheChildBeforeToRecalculate() {
        club.setMedicalMode(MedicalMode.ON);
        when(medicalRequirement.requiredProfileIds(anyCollection())).thenReturn(Set.of(onlyHere));
        when(medicalRequirement.requiringClubIdsNow(Set.of(onlyHere))).thenReturn(Map.of(onlyHere, Set.of(club.getId())));
        when(medicalRequirement.requiredProfileIdsNow(anyCollection())).thenReturn(Set.of());

        service.updateClubSettings(club.getId(), clubRequest("OFF", null, true, null), admin);

        verify(medicalRequirement).recalculate(List.of(onlyHere, alsoElsewhere), Map.of(onlyHere, Set.of(club.getId())));
        verify(userAccessService).requireOwnClubAdmin(admin, club.getId());
    }

    @Test
    void retentionMustBeOneThreeOrFiveYears() {
        assertThatThrownBy(() -> service.updateClubSettings(club.getId(),
                clubRequest(null, null, null, 2), admin))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void aTeamAcceptsInheritButAClubDoesNot() {
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setClub(club);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(medicalRequirement.requiredProfileIds(anyCollection())).thenReturn(Set.of());

        service.updateTeamMode(team.getId(), new UpdateTeamMedicalModeRequest("inherit", null, null, null), admin);
        assertThat(team.getMedicalMode()).isEqualTo(MedicalMode.INHERIT);

        assertThatThrownBy(() -> service.updateClubSettings(club.getId(),
                clubRequest("INHERIT", true, null, null), admin))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void turningATeamOnInAClubThatIsOffNeedsTheAcknowledgement() {
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setClub(club);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(medicalRequirement.requiredProfileIds(anyCollection())).thenReturn(Set.of());

        assertThatThrownBy(() -> service.updateTeamMode(team.getId(), new UpdateTeamMedicalModeRequest("ON", false, null, null), admin))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getMessageCode()).isEqualTo("error.medical.acknowledgement_required"));
        assertThat(team.getMedicalMode()).isEqualTo(MedicalMode.INHERIT);

        service.updateTeamMode(team.getId(), new UpdateTeamMedicalModeRequest("ON", true, null, null), admin);
        assertThat(team.getMedicalMode()).isEqualTo(MedicalMode.ON);
        verify(auditEventService).record(any(), any(), any(), any(), any(), eq("mode=ON,evidence=INHERIT,acknowledged=true"));
    }

    @Test
    void inAClubThatIsOnATeamCanBeTurnedOnWithoutAnotherAcknowledgement() {
        club.setMedicalMode(MedicalMode.ON);
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setClub(club);
        team.setMedicalMode(MedicalMode.OFF);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(medicalRequirement.requiredProfileIds(anyCollection())).thenReturn(Set.of());

        service.updateTeamMode(team.getId(), new UpdateTeamMedicalModeRequest("ON", null, null, null), admin);

        assertThat(team.getMedicalMode()).isEqualTo(MedicalMode.ON);
    }

    @Test
    void anUnsecuredStorageRefusesTurningAClubOnButNotTurningItOff() {
        doThrow(notSecure()).when(storageGuard).requireSecure();
        // Only a club that asks for the redacted copy needs a secure storage.
        club.setMedicalEvidence(MedicalEvidence.REDACTED_FILE);
        when(medicalRequirement.requiredProfileIds(anyCollection())).thenReturn(Set.of());

        assertThatThrownBy(() -> service.updateClubSettings(club.getId(),
                clubRequest("ON", true, null, null), admin))
                .isInstanceOf(ExternalServiceException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getMessageCode()).isEqualTo("error.medical.storage_not_secure"));
        assertThat(club.getMedicalMode()).isEqualTo(MedicalMode.OFF);
        verify(clubRepository, never()).save(any());

        club.setMedicalMode(MedicalMode.ON);
        service.updateClubSettings(club.getId(), clubRequest("OFF", null, null, 3), admin);
        assertThat(club.getMedicalMode()).isEqualTo(MedicalMode.OFF);
    }

    @Test
    void anUnsecuredStorageRefusesATeamThatStartsCollecting() {
        doThrow(notSecure()).when(storageGuard).requireSecure();
        club.setMedicalEvidence(MedicalEvidence.REDACTED_FILE);
        when(medicalRequirement.requiredProfileIds(anyCollection())).thenReturn(Set.of());
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setClub(club);
        team.setMedicalMode(MedicalMode.OFF);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> service.updateTeamMode(team.getId(), new UpdateTeamMedicalModeRequest("ON", true, null, null), admin))
                .isInstanceOf(ExternalServiceException.class);
        // INHERIT in an ON club starts collection as well.
        club.setMedicalMode(MedicalMode.ON);
        assertThatThrownBy(() -> service.updateTeamMode(team.getId(), new UpdateTeamMedicalModeRequest("INHERIT", null, null, null), admin))
                .isInstanceOf(ExternalServiceException.class);
        assertThat(team.getMedicalMode()).isEqualTo(MedicalMode.OFF);

        // INHERIT in an OFF club does not collect, so it is saved.
        club.setMedicalMode(MedicalMode.OFF);
        service.updateTeamMode(team.getId(), new UpdateTeamMedicalModeRequest("INHERIT", null, null, null), admin);
        assertThat(team.getMedicalMode()).isEqualTo(MedicalMode.INHERIT);
    }

    @Test
    void switchingFromTheFileToTheDateOnlyNeedsConfirmationAndKeepsTheRow() {
        club.setMedicalMode(MedicalMode.ON);
        club.setMedicalEvidence(MedicalEvidence.REDACTED_FILE);
        MedicalCertificate file = certificate(onlyHere);
        String key = file.getObjectKey();
        when(medicalRequirement.requiredProfileIds(anyCollection())).thenReturn(Set.of(onlyHere, alsoElsewhere));
        when(medicalRequirement.fileRequiredProfileIds(anyCollection())).thenReturn(Set.of(onlyHere, alsoElsewhere));
        when(medicalRequirement.requiredProfileIdsNow(anyCollection())).thenReturn(Set.of(onlyHere, alsoElsewhere));
        // The other child is still asked for a file by another club.
        when(medicalRequirement.fileRequiredProfileIdsNow(anyCollection())).thenReturn(Set.of(alsoElsewhere));
        when(certificateRepository.findCurrentByProfileIdIn(List.of(onlyHere))).thenReturn(List.of(file));

        assertThatThrownBy(() -> service.updateClubSettings(club.getId(), evidenceRequest("DATE_ONLY", false), admin))
                .isInstanceOf(ConflictWithPropertiesException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getMessageCode()).isEqualTo("error.medical.file_deletion_confirmation_required");
                    assertThat(((ConflictWithPropertiesException) ex).getProperties())
                            .containsEntry("affectedFiles", 1).containsEntry("affectedProfiles", 0);
                });
        verify(certificateRepository, never()).save(any());

        service.updateClubSettings(club.getId(), evidenceRequest("DATE_ONLY", true), admin);

        assertThat(club.getMedicalEvidence()).isEqualTo(MedicalEvidence.DATE_ONLY);
        assertThat(file.getObjectKey()).isNull();
        assertThat(file.getWithdrawnAt()).isNull();
        verify(fileStore).deleteAfterCommit(List.of(new MedicalFileStore.KeyRef(key, file.getId())));
        verify(auditEventService).record(eq("MEDICAL_FILE_REMOVED_BY_POLICY"), any(), any(), eq("MEDICAL_CERTIFICATE"),
                eq(file.getId().toString()), eq("reason=evidence_date_only"));
    }

    @Test
    void askingForTheFileNeedsASecureStorageButTheDateOnlyModeDoesNot() {
        doThrow(notSecure()).when(storageGuard).requireSecure();
        when(medicalRequirement.requiredProfileIds(anyCollection())).thenReturn(Set.of());

        assertThatThrownBy(() -> service.updateClubSettings(club.getId(), evidenceRequest("REDACTED_FILE", null), admin))
                .isInstanceOf(ExternalServiceException.class);
        assertThat(club.getMedicalEvidence()).isEqualTo(MedicalEvidence.DATE_ONLY);

        service.updateClubSettings(club.getId(), clubRequest("ON", true, null, null), admin);
        assertThat(club.getMedicalMode()).isEqualTo(MedicalMode.ON);
    }

    @Test
    void anUnknownEvidenceIsRefusedAndAClubCannotInherit() {
        assertThatThrownBy(() -> service.updateClubSettings(club.getId(), evidenceRequest("PHOTO", null), admin))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getMessageCode()).isEqualTo("error.medical.invalid_evidence"));
        assertThatThrownBy(() -> service.updateClubSettings(club.getId(), evidenceRequest("INHERIT", null), admin))
                .isInstanceOf(BadRequestException.class);

        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setClub(club);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(medicalRequirement.requiredProfileIds(anyCollection())).thenReturn(Set.of());
        service.updateTeamMode(team.getId(), new UpdateTeamMedicalModeRequest(null, null, null, "DATE_ONLY"), admin);
        assertThat(team.getMedicalEvidence()).isEqualTo(MedicalEvidence.DATE_ONLY);
        assertThat(team.getMedicalMode()).isEqualTo(MedicalMode.INHERIT);
    }

    @Test
    void theCoachFileAccessNeedsTheAcknowledgementAndAFileModeAndTurnsOffAtOnce() {
        when(medicalRequirement.requiredProfileIds(anyCollection())).thenReturn(Set.of());

        assertThatThrownBy(() -> service.updateClubSettings(club.getId(), coachAccess(true, false), admin))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getMessageCode()).isEqualTo("error.medical.acknowledgement_required"));
        assertThatThrownBy(() -> service.updateClubSettings(club.getId(), coachAccess(true, true), admin))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getMessageCode()).isEqualTo("error.medical.coach_file_access_without_file"));
        assertThat(club.isMedicalCoachFileAccess()).isFalse();

        // One team asking for the file is enough.
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setClub(club);
        team.setMedicalEvidence(MedicalEvidence.REDACTED_FILE);
        when(teamRepository.findByClub_Id(club.getId())).thenReturn(List.of(team));
        var settings = service.updateClubSettings(club.getId(), coachAccess(true, true), admin);
        assertThat(settings.coachFileAccess()).isTrue();
        assertThat(club.getMedicalCoachFileAccessAcknowledgedAt()).isEqualTo(NOW);
        assertThat(club.getMedicalCoachFileAccessAcknowledgedByUserId()).isEqualTo(admin.getId());
        verify(auditEventService).record(eq("MEDICAL_SETTINGS_SET"), any(), any(), eq("CLUB"), any(),
                argThat((String details) -> details.contains("coachFileAccess=true,coachFileAccessAcknowledged=true")));

        service.updateClubSettings(club.getId(), coachAccess(false, null), admin);
        assertThat(club.isMedicalCoachFileAccess()).isFalse();
        verify(fileStore, never()).deleteAfterCommit(anyCollection());
    }

    private static UpdateClubMedicalSettingsRequest coachAccess(Boolean on, Boolean acknowledged) {
        return new UpdateClubMedicalSettingsRequest(null, null, null, null, null, null, null, null, on, acknowledged);
    }

    private static UpdateClubMedicalSettingsRequest evidenceRequest(String evidence, Boolean confirmDeletion) {
        return new UpdateClubMedicalSettingsRequest(null, null, confirmDeletion, null, null, null, null, evidence, null, null);
    }

    private static ExternalServiceException notSecure() {
        return new ExternalServiceException("error.medical.storage_not_secure", new Object[0], "not secure");
    }

    private static MedicalCertificate certificate(UUID profileId) {
        MedicalCertificate row = new MedicalCertificate();
        row.setId(UUID.randomUUID());
        row.setPlayerProfileId(profileId);
        row.setObjectKey("medical/" + profileId + "/" + row.getId());
        row.setValidUntil(LocalDate.of(2027, 5, 3));
        row.setSubmittedByRole(MedicalSubmitterRole.PARENT);
        row.setCompletedAt(NOW.minusSeconds(100));
        return row;
    }

    private static UpdateClubMedicalSettingsRequest clubRequest(String mode, Boolean acknowledged, Boolean confirmDeletion, Integer retention) {
        return new UpdateClubMedicalSettingsRequest(mode, acknowledged, confirmDeletion, retention, null, null, null, null, null, null);
    }
}
