package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.AppException;
import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.MedicalAdminAccess;
import com.example.footballanalysis.model.db.MedicalCertificate;
import com.example.footballanalysis.model.db.MedicalParentSeen;
import com.example.footballanalysis.model.db.MedicalParentSeenId;
import com.example.footballanalysis.model.db.MedicalResult;
import com.example.footballanalysis.model.db.MedicalSubmitterRole;
import com.example.footballanalysis.model.db.PlayerParentId;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.user.Admin;
import com.example.footballanalysis.model.db.user.ClubAdmin;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.SetMedicalUploadRightRequest;
import com.example.footballanalysis.model.requests.SubmitMedicalCertificateRequest;
import com.example.footballanalysis.repository.AuditEventRepository;
import com.example.footballanalysis.repository.MedicalAdminAccessRepository;
import com.example.footballanalysis.repository.MedicalCertificateRepository;
import com.example.footballanalysis.repository.MedicalParentSeenRepository;
import com.example.footballanalysis.repository.PlayerParentRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The age rules and the parent's unread mark (docs/sportorvosi-jogszeruseg.md, PR 12), with the real
 * {@link MedicalAccessService} and {@link MedicalAgePolicy} on a fixed Budapest clock.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MedicalParentRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

    @Mock private MedicalCertificateRepository certificateRepository;
    @Mock private MedicalAdminAccessRepository adminAccessRepository;
    @Mock private PlayerProfileRepository playerProfileRepository;
    @Mock private PlayerParentRepository playerParentRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private TeamRosterRepository teamRosterRepository;
    @Mock private TeamMembershipQuery teamMembershipQuery;
    @Mock private UserRepository userRepository;
    @Mock private UserAccessService userAccessService;
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
    private Parent otherParent;
    private Player player;
    private ClubAdmin clubAdmin;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("Europe/Budapest"));
        MedicalAccessService access = new MedicalAccessService(userAccessService, playerParentRepository, teamMembershipQuery,
                medicalRequirement, playerProfileRepository, new MedicalAgePolicy(clock));
        service = new MedicalCertificateService(certificateRepository, adminAccessRepository, playerProfileRepository, teamRepository,
                teamRosterRepository, teamMembershipQuery, userRepository, userAccessService, access, medicalRequirement, fileStore,
                storageGuard, compliance, auditEventService, auditEventRepository, parentSeenRepository, clock, transactionManager);
        profile = new PlayerProfile();
        profile.setId(UUID.randomUUID());
        parent = parent();
        otherParent = parent();
        player = new Player();
        player.setId(UUID.randomUUID());
        player.setRole(UserRole.PLAYER);
        profile.setLinkedUser(player);
        clubAdmin = new ClubAdmin();
        clubAdmin.setId(UUID.randomUUID());
        clubAdmin.setRole(UserRole.CLUB_ADMIN);
        when(playerProfileRepository.findById(profile.getId())).thenReturn(Optional.of(profile));
        when(playerProfileRepository.findByIdForUpdate(profile.getId())).thenReturn(Optional.of(profile));
        when(playerProfileRepository.findByLinkedUser_Id(player.getId())).thenReturn(Optional.of(profile));
        when(playerParentRepository.existsById(new PlayerParentId(parent.getId(), profile.getId()))).thenReturn(true);
        when(playerParentRepository.existsById(new PlayerParentId(otherParent.getId(), profile.getId()))).thenReturn(true);
        when(userAccessService.hasHat(clubAdmin, UserRole.CLUB_ADMIN)).thenReturn(true);
        UUID clubId = UUID.randomUUID();
        when(medicalRequirement.requiringClubIds(profile.getId())).thenReturn(Set.of(clubId));
        when(userAccessService.isClubAdmin(clubAdmin, clubId)).thenReturn(true);
        when(medicalRequirement.isRequired(profile.getId())).thenReturn(true);
        when(medicalRequirement.requiredProfileIds(anyCollection())).thenReturn(Set.of(profile.getId()));
        when(certificateRepository.findHistoryByProfileId(profile.getId())).thenReturn(List.of());
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.empty());
        when(certificateRepository.findPendingByProfileId(profile.getId())).thenReturn(List.of());
        when(compliance.isClubReady(any())).thenReturn(true);
        when(fileStore.uploadUrl(anyString(), anyString(), anyLong())).thenReturn("http://minio/put");
    }

    // ---------------------------------------------------------------- upload right

    @Test
    void underEighteenOnlyTheParentSetsTheUploadRightEvenWithAnEmptyDateOfBirth() {
        for (LocalDate birth : new LocalDate[]{TODAY.minusYears(17), null}) {
            profile.setDateOfBirth(birth);
            assertCode(() -> service.setUploadRight(profile.getId(), new SetMedicalUploadRightRequest(true), clubAdmin),
                    "error.medical.upload_right_parent_only");
            Admin operator = new Admin();
            operator.setId(UUID.randomUUID());
            operator.setRole(UserRole.ADMIN);
            assertCode(() -> service.setUploadRight(profile.getId(), new SetMedicalUploadRightRequest(true), operator),
                    "error.auth.forbidden");
            assertThat(profile.isCanUploadMedical()).isFalse();

            service.setUploadRight(profile.getId(), new SetMedicalUploadRightRequest(true), parent);
            assertThat(profile.isCanUploadMedical()).isTrue();
            profile.setCanUploadMedical(false);
        }
    }

    @Test
    void fromEighteenTheUploadRightIsNotSetAtAll() {
        profile.setDateOfBirth(TODAY.minusYears(18));

        assertCode(() -> service.setUploadRight(profile.getId(), new SetMedicalUploadRightRequest(true), parent),
                "error.medical.upload_right_adult");
    }

    // ---------------------------------------------------------------- the adult player

    @Test
    void theAdultPlayerSubmitsOpensAndWithdrawsWithoutTheUploadRight() {
        profile.setDateOfBirth(TODAY.minusYears(19));
        var upload = service.submit(profile.getId(), new SubmitMedicalCertificateRequest("VERSENYEZHET", LocalDate.of(2027, 5, 3),
                true, null, null, null), player);
        assertThat(upload.completed()).isTrue();
        ArgumentCaptor<MedicalCertificate> saved = ArgumentCaptor.forClass(MedicalCertificate.class);
        verify(certificateRepository).save(saved.capture());
        assertThat(saved.getValue().getSubmittedByRole()).isEqualTo(MedicalSubmitterRole.PLAYER);

        MedicalCertificate current = fileRow();
        when(medicalRequirement.fileRequired(profile.getId())).thenReturn(true);
        service.openFile(profile.getId(), current.getId(), player);
        service.withdraw(profile.getId(), current.getId(), player);
        assertThat(current.getWithdrawnAt()).isEqualTo(NOW);
    }

    @Test
    void aMinorPlayerWithoutTheRightCannotWithdrawOrOpen() {
        profile.setDateOfBirth(TODAY.minusYears(17).plusDays(1));
        MedicalCertificate current = fileRow();

        assertCode(() -> service.withdraw(profile.getId(), current.getId(), player), "error.auth.forbidden");
        assertCode(() -> service.openFile(profile.getId(), current.getId(), player), "error.auth.forbidden");
    }

    @Test
    void theParentOfAnAdultSeesNothingUntilThePlayerAllowsIt() {
        profile.setDateOfBirth(TODAY.minusYears(18));
        fileRow();

        assertThatThrownBy(() -> service.getStatus(profile.getId(), parent)).isInstanceOf(ForbiddenException.class);
        var children = service.childSummaries(List.of(profile), parent);
        assertThat(children.get(profile.getId()).adultManaged()).isTrue();
        assertThat(children.get(profile.getId()).required()).isFalse();
        assertThat(children.get(profile.getId()).status()).isNull();
        assertThat(children.get(profile.getId()).unseen()).isFalse();

        var answer = service.setParentAccess(new SetMedicalUploadRightRequest(true), player);
        assertThat(answer.allowed()).isTrue();
        verify(auditEventService).record(eq("MEDICAL_PARENT_ACCESS_SET"), eq(player.getId()), any(), eq("PLAYER_PROFILE"), any(), any());
        assertThat(service.getStatus(profile.getId(), parent).required()).isTrue();
        assertThat(service.childSummaries(List.of(profile), parent).get(profile.getId()).adultManaged()).isFalse();
    }

    @Test
    void theAccessChangesTheDayOfTheEighteenthBirthday() {
        profile.setDateOfBirth(LocalDate.of(2008, 10, 30));
        fileRow();
        assertThat(service.getStatus(profile.getId(), parent).required()).isTrue();

        profile.setDateOfBirth(LocalDate.of(2008, 10, 29));
        assertThatThrownBy(() -> service.getStatus(profile.getId(), parent)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aMinorCannotSetTheParentAccess() {
        profile.setDateOfBirth(TODAY.minusYears(16));

        assertCode(() -> service.setParentAccess(new SetMedicalUploadRightRequest(true), player), "error.medical.parent_access_minor");
    }

    @Test
    void theAdultPlayerSeesTheAccessesNoClubSeesAndItsOwnFlags() {
        profile.setDateOfBirth(TODAY.minusYears(20));
        fileRow();
        MedicalAdminAccess access = clublessAccess(NOW.minusSeconds(60));
        when(adminAccessRepository.findWithoutClubByProfileId(profile.getId())).thenReturn(List.of(access));

        var mine = service.getMyStatus(player);

        assertThat(mine.adult()).isTrue();
        assertThat(mine.parentAccessAllowed()).isFalse();
        assertThat(mine.certificateId()).isNotNull();
        assertThat(mine.adminAccesses()).hasSize(1);
        assertThatThrownBy(() -> service.getStatus(profile.getId(), parent)).isInstanceOf(ForbiddenException.class);
    }

    // ---------------------------------------------------------------- unread mark

    @Test
    void aCertificateSomeoneElseEnteredIsUnreadForEachParentUntilTheyOpenIt() {
        profile.setDateOfBirth(TODAY.minusYears(12));
        MedicalCertificate current = fileRow();
        current.setSubmittedByUserId(UUID.randomUUID());
        current.setSubmittedByRole(MedicalSubmitterRole.COACH);

        assertThat(service.getStatus(profile.getId(), parent).unseen()).isTrue();
        assertThat(service.childSummaries(List.of(profile), parent).get(profile.getId()).unseen()).isTrue();

        MedicalParentSeen seen = new MedicalParentSeen();
        seen.setId(new MedicalParentSeenId(profile.getId(), parent.getId()));
        seen.setCertificateId(current.getId());
        seen.setSeenAt(NOW);
        service.markSeen(profile.getId(), current.getId(), parent);
        verify(parentSeenRepository).save(org.mockito.ArgumentMatchers.argThat(row -> row.getCertificateId().equals(current.getId())
                && row.getId().getParentUserId().equals(parent.getId()) && NOW.equals(row.getSeenAt())));

        when(parentSeenRepository.findById(new MedicalParentSeenId(profile.getId(), parent.getId()))).thenReturn(Optional.of(seen));
        when(parentSeenRepository.findByParentAndProfileIdIn(eq(parent.getId()), anyCollection())).thenReturn(List.of(seen));
        assertThat(service.getStatus(profile.getId(), parent).unseen()).isFalse();
        assertThat(service.childSummaries(List.of(profile), parent).get(profile.getId()).unseen()).isFalse();
        // The other parent has not looked yet.
        assertThat(service.getStatus(profile.getId(), otherParent).unseen()).isTrue();
    }

    @Test
    void theParentsOwnSubmissionIsNotUnreadButANewerBreakGlassAccessIs() {
        profile.setDateOfBirth(TODAY.minusYears(12));
        MedicalCertificate current = fileRow();
        current.setSubmittedByUserId(parent.getId());
        assertThat(service.getStatus(profile.getId(), parent).unseen()).isFalse();

        MedicalParentSeen seen = new MedicalParentSeen();
        seen.setId(new MedicalParentSeenId(profile.getId(), parent.getId()));
        seen.setCertificateId(current.getId());
        seen.setSeenAt(NOW.minusSeconds(3_600));
        when(parentSeenRepository.findById(seen.getId())).thenReturn(Optional.of(seen));
        MedicalAdminAccess access = clublessAccess(NOW.minusSeconds(60));
        when(adminAccessRepository.findWithoutClubByProfileId(profile.getId())).thenReturn(List.of(access));

        var status = service.getStatus(profile.getId(), parent);
        assertThat(status.unseen()).isTrue();
        assertThat(status.adminAccesses()).hasSize(1);
    }

    @Test
    void onlyTheCurrentRowCanBeAcknowledgedAndOnlyByAParent() {
        profile.setDateOfBirth(TODAY.minusYears(12));
        MedicalCertificate current = fileRow();

        assertCode(() -> service.markSeen(profile.getId(), UUID.randomUUID(), parent), "error.medical.not_current");
        Parent stranger = parent();
        assertCode(() -> service.markSeen(profile.getId(), current.getId(), stranger), "error.auth.forbidden");
        verify(parentSeenRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- helpers

    private MedicalCertificate fileRow() {
        MedicalCertificate row = new MedicalCertificate();
        row.setId(UUID.randomUUID());
        row.setPlayerProfileId(profile.getId());
        row.setResult(MedicalResult.VERSENYEZHET);
        row.setValidUntil(LocalDate.of(2027, 5, 3));
        row.setEvidence(MedicalCertificate.Evidence.FILE);
        row.setObjectKey("medical/" + profile.getId() + "/" + row.getId());
        row.setContentType("application/pdf");
        row.setSizeBytes(1000L);
        row.setSubmittedByRole(MedicalSubmitterRole.PARENT);
        row.setCreatedAt(NOW.minusSeconds(86_400));
        row.setCompletedAt(NOW.minusSeconds(86_000));
        when(certificateRepository.findCurrentByProfileId(profile.getId())).thenReturn(Optional.of(row));
        when(certificateRepository.findById(row.getId())).thenReturn(Optional.of(row));
        when(certificateRepository.findCurrentByProfileIdIn(anyCollection())).thenReturn(List.of(row));
        return row;
    }

    private MedicalAdminAccess clublessAccess(Instant at) {
        MedicalAdminAccess access = new MedicalAdminAccess();
        access.setId(UUID.randomUUID());
        access.setPlayerProfileId(profile.getId());
        access.setAction(MedicalAdminAccess.Action.READ);
        access.setReason("The parent asked what is stored about the child.");
        access.setCreatedAt(at);
        when(adminAccessRepository.findWithoutClubByProfileIdIn(anyCollection())).thenReturn(List.of(access));
        return access;
    }

    private static Parent parent() {
        Parent parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setRole(UserRole.PARENT);
        return parent;
    }

    private static void assertCode(ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOfAny(BadRequestException.class, ConflictException.class, ForbiddenException.class, NotFoundException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getMessageCode()).isEqualTo(code));
    }
}
