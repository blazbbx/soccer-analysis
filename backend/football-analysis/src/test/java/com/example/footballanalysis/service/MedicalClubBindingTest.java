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
import com.example.footballanalysis.model.db.MedicalResult;
import com.example.footballanalysis.model.db.MedicalSubmitterRole;
import com.example.footballanalysis.model.db.PlayerParentId;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.TeamRoster;
import com.example.footballanalysis.model.db.TeamRosterId;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.MedicalInPersonRequest;
import com.example.footballanalysis.model.requests.MedicalShareRequest;
import com.example.footballanalysis.model.requests.MedicalVerificationRequest;
import com.example.footballanalysis.model.requests.SubmitMedicalCertificateRequest;
import com.example.footballanalysis.model.responses.MedicalClubItem;
import com.example.footballanalysis.model.responses.MedicalNoticeItem;
import com.example.footballanalysis.model.responses.MedicalShareableClubItem;
import com.example.footballanalysis.model.responses.MedicalStatusResponse;
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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
 * The certificate bound to its clubs (docs/funkciok/sportorvosi/sportorvosi-klubhoz-kotes.md), with the real
 * {@link MedicalAccessService} and {@link MedicalRequirementService}. Anna plays in Kék FC and Piros SE; both teams
 * collect. Whether a club is ready is switched per test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MedicalClubBindingTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private static final LocalDate UNTIL = LocalDate.of(2027, 5, 3);

    @Mock private MedicalCertificateRepository certificateRepository;
    @Mock private MedicalAdminAccessRepository adminAccessRepository;
    @Mock private PlayerProfileRepository playerProfileRepository;
    @Mock private PlayerParentRepository playerParentRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private TeamRosterRepository teamRosterRepository;
    @Mock private TeamMembershipQuery teamMembershipQuery;
    @Mock private UserRepository userRepository;
    @Mock private UserAccessService userAccessService;
    @Mock private MedicalFileStore fileStore;
    @Mock private MedicalStorageGuard storageGuard;
    @Mock private MedicalCompliance compliance;
    @Mock private AuditEventService auditEventService;
    @Mock private AuditEventRepository auditEventRepository;
    @Mock private MedicalParentSeenRepository parentSeenRepository;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private EntityManager entityManager;

    private MedicalCertificateService service;
    private PlayerProfile anna;
    private Club kek;
    private Club piros;
    private Team kekTeam;
    private Team pirosTeam;
    private User parent;
    private User kekAdmin;
    private User pirosAdmin;
    private User kekCoach;
    private User pirosCoach;
    /** The repository's one current row, and the last pending upload. */
    private MedicalCertificate current;
    private MedicalCertificate pending;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("Europe/Budapest"));
        MedicalRequirementService requirement = new MedicalRequirementService(teamRosterRepository, playerProfileRepository,
                certificateRepository, entityManager, clock, compliance);
        MedicalAccessService access = new MedicalAccessService(userAccessService, playerParentRepository, teamMembershipQuery,
                requirement, playerProfileRepository, new MedicalAgePolicy(clock));
        service = new MedicalCertificateService(certificateRepository, adminAccessRepository, playerProfileRepository, teamRepository,
                teamRosterRepository, teamMembershipQuery, userRepository, userAccessService, access, requirement, fileStore,
                storageGuard, compliance, auditEventService, auditEventRepository, parentSeenRepository, clock, transactionManager);

        anna = new PlayerProfile();
        anna.setId(UUID.randomUUID());
        when(playerProfileRepository.findById(anna.getId())).thenReturn(Optional.of(anna));
        when(playerProfileRepository.findByIdForUpdate(anna.getId())).thenReturn(Optional.of(anna));

        kek = club("Kék FC");
        piros = club("Piros SE");
        kekTeam = team(kek);
        pirosTeam = team(piros);
        when(teamRosterRepository.findWithTeamAndClubByProfileIdIn(anyCollection()))
                .thenReturn(List.of(roster(kekTeam), roster(pirosTeam)));
        when(compliance.isClubReady(kek)).thenReturn(true);
        when(compliance.isClubReady(piros)).thenReturn(false);

        parent = user(UserRole.PARENT);
        when(playerParentRepository.existsById(new PlayerParentId(parent.getId(), anna.getId()))).thenReturn(true);
        kekAdmin = clubAdmin(kek);
        pirosAdmin = clubAdmin(piros);
        kekCoach = coach(kekTeam);
        pirosCoach = coach(pirosTeam);

        when(certificateRepository.findCurrentByProfileId(anna.getId()))
                .thenAnswer(call -> Optional.ofNullable(current).filter(MedicalCertificate::isCurrent));
        when(certificateRepository.findCurrentByProfileIdIn(anyCollection()))
                .thenAnswer(call -> current != null && current.isCurrent() ? List.of(current) : List.of());
        when(certificateRepository.findById(any())).thenAnswer(call -> java.util.stream.Stream.of(current, pending)
                .filter(row -> row != null && row.getId().equals(call.getArgument(0))).findFirst());
        when(fileStore.uploadUrl(anyString(), anyString(), anyLong())).thenReturn("http://minio/put");
        when(fileStore.size(any())).thenReturn(Optional.of(1000L));
        when(fileStore.firstBytes(any(), eq(8))).thenReturn(Optional.of(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));
        when(certificateRepository.findHistoryByProfileId(anna.getId())).thenReturn(List.of());
        when(certificateRepository.findPendingByProfileId(anna.getId())).thenReturn(List.of());
        when(certificateRepository.save(any())).thenAnswer(call -> keep(call.getArgument(0)));
        when(certificateRepository.saveAndFlush(any())).thenAnswer(call -> keep(call.getArgument(0)));
    }

    // ---------------------------------------------------------------- submit

    @Test
    void aClubThatIsNotReadyDoesNotHoldUpTheOtherAndGetsNothing() {
        submitByParent();

        assertThat(current.getClubIds()).containsExactly(kek.getId());
        verify(auditEventService).record(eq("MEDICAL_CERTIFICATE_SUBMITTED"), eq(parent.getId()), eq("MEDICAL_CERTIFICATE"),
                eq(current.getId().toString()), eq("profileId=" + anna.getId() + ",evidence=DATE_ONLY,notices=" + kek.getId() + ":2"));

        MedicalStatusResponse status = service.getStatus(anna.getId(), parent);
        assertThat(status.notices()).extracting(MedicalNoticeItem::clubId).containsExactly(kek.getId());
        assertThat(status.sharedWith()).containsExactly(new MedicalClubItem(kek.getId(), "Kék FC"));
        assertThat(status.shareableClubs()).isEmpty();
    }

    @Test
    void withoutAnyReadyClubNothingCanBeSubmitted() {
        when(compliance.isClubReady(kek)).thenReturn(false);

        assertCode(this::submitByParent, "error.medical.club_not_ready");
        verify(certificateRepository, never()).save(any());
    }

    @Test
    void onlyTheRecipientsTeamsDecideTheFileMode() {
        // Only Piros SE asks for the copy, and it is not ready: the date is enough.
        pirosTeam.setMedicalEvidence(MedicalEvidence.REDACTED_FILE);
        assertThat(service.getStatus(anna.getId(), parent).fileRequired()).isFalse();
        submitByParent();
        assertThat(current.getEvidence()).isEqualTo(MedicalCertificate.Evidence.DATE_ONLY);

        when(compliance.isClubReady(piros)).thenReturn(true);
        assertThat(service.getStatus(anna.getId(), parent).fileRequired()).isTrue();
    }

    @Test
    void completeDropsARecipientThatIsNoLongerReadyAndRefusesWhenNoneIsLeft() {
        kekTeam.setMedicalEvidence(MedicalEvidence.REDACTED_FILE);
        when(compliance.isClubReady(piros)).thenReturn(true);
        uploadByParent();
        assertThat(pending.getClubIds()).containsExactly(kek.getId(), piros.getId());

        when(compliance.isClubReady(piros)).thenReturn(false);
        service.complete(anna.getId(), pending.getId(), parent);
        assertThat(current.getId()).isEqualTo(pending.getId());
        assertThat(current.getClubIds()).containsExactly(kek.getId());

        uploadByParent();
        when(compliance.isClubReady(kek)).thenReturn(false);
        MedicalCertificate stuck = pending;
        assertCode(() -> service.complete(anna.getId(), stuck.getId(), parent), "error.medical.club_not_ready");
        assertThat(stuck.isPending()).isTrue();
    }

    @Test
    void completeAddsNoClubThatBecameReadyDuringTheUpload() {
        kekTeam.setMedicalEvidence(MedicalEvidence.REDACTED_FILE);
        uploadByParent();
        when(compliance.isClubReady(piros)).thenReturn(true);

        service.complete(anna.getId(), pending.getId(), parent);

        assertThat(current.getClubIds()).containsExactly(kek.getId());
    }

    // ---------------------------------------------------------------- what a club sees

    @Test
    void aClubThatIsNotARecipientSeesTheChildAsMissingAndCannotActOnTheRow() {
        submitByParent();
        // Piros SE becomes ready later: the row was not meant for it.
        when(compliance.isClubReady(piros)).thenReturn(true);

        MedicalStatusResponse kekView = service.getStatus(anna.getId(), kekAdmin);
        assertThat(kekView.status()).isEqualTo("UNVERIFIED");
        assertThat(kekView.certificateId()).isEqualTo(current.getId());

        MedicalStatusResponse pirosView = service.getStatus(anna.getId(), pirosAdmin);
        assertThat(pirosView.status()).isEqualTo("MISSING");
        assertThat(pirosView.certificateId()).isNull();
        assertThat(pirosView.history()).isEmpty();
        assertThat(pirosView.sharedWith()).isNull();
        assertThat(pirosView.shareableClubs()).isNull();
        assertThat(service.getStatus(anna.getId(), pirosCoach).status()).isEqualTo("MISSING");
        assertThat(service.getStatus(anna.getId(), kekCoach).status()).isEqualTo("UNVERIFIED");

        MedicalVerificationRequest original = new MedicalVerificationRequest("VERSENYEZHET", UNTIL, "ORIGINAL");
        assertCode(() -> service.verify(anna.getId(), original, pirosCoach), "error.auth.forbidden");
        assertCode(() -> service.verify(anna.getId(), original, pirosAdmin), "error.auth.forbidden");
        assertCode(() -> service.unverify(anna.getId(), pirosAdmin), "error.auth.forbidden");
        assertCode(() -> service.withdraw(anna.getId(), current.getId(), pirosAdmin), "error.auth.forbidden");
        assertThat(current.isVerified()).isFalse();

        service.verify(anna.getId(), original, kekCoach);
        assertThat(current.getVerifiedByRole()).isEqualTo(MedicalSubmitterRole.COACH);
    }

    @Test
    void aRecipientThatIsNoLongerReadySeesNothingUntilItIsReadyAgain() {
        submitByParent();
        when(compliance.isClubReady(kek)).thenReturn(false);

        assertThat(service.getStatus(anna.getId(), kekAdmin).status()).isEqualTo("MISSING");
        assertCode(() -> service.verify(anna.getId(), new MedicalVerificationRequest("VERSENYEZHET", UNTIL, "ORIGINAL"), kekCoach),
                "error.auth.forbidden");
        // The row stays: the club still requires the certificate, so nothing is withdrawn.
        assertThat(current.isCurrent()).isTrue();

        when(compliance.isClubReady(kek)).thenReturn(true);
        assertThat(service.getStatus(anna.getId(), kekAdmin).status()).isEqualTo("UNVERIFIED");
    }

    @Test
    void theTeamListShowsTheLightOnlyWhereTheRowIsMeantForTheTeamsClub() {
        submitByParent();
        when(compliance.isClubReady(piros)).thenReturn(true);
        when(teamRepository.findById(kekTeam.getId())).thenReturn(Optional.of(kekTeam));
        when(teamRepository.findById(pirosTeam.getId())).thenReturn(Optional.of(pirosTeam));
        when(teamRosterRepository.findProfileIdsByTeamId(any())).thenReturn(List.of(anna.getId()));

        assertThat(service.teamStatus(kekTeam.getId(), kekAdmin).items().get(0).status()).isEqualTo("UNVERIFIED");
        assertThat(service.teamStatus(pirosTeam.getId(), pirosAdmin).items().get(0).status()).isEqualTo("MISSING");
    }

    @Test
    void aClubThatIsNotReadyIsToldItCannotCollectAndItsListShowsNoCertificate() {
        submitByParent();
        when(teamRepository.findByClub_Id(piros.getId())).thenReturn(List.of(pirosTeam));
        when(teamRepository.findByClub_Id(kek.getId())).thenReturn(List.of(kekTeam));
        when(teamRosterRepository.findProfileIdsByTeamId(any())).thenReturn(List.of(anna.getId()));
        when(playerProfileRepository.findAllById(anyCollection())).thenReturn(List.of(anna));

        var pirosList = service.clubStatus(piros.getId(), null, null, null, null, pirosAdmin);
        assertThat(pirosList.ready()).isFalse();
        assertThat(pirosList.page().items()).extracting(item -> item.status()).containsExactly("MISSING");
        assertThat(service.clubStatus(kek.getId(), null, null, null, null, kekAdmin).ready()).isTrue();

        assertThat(service.getStatus(anna.getId(), pirosAdmin).submissionOpen()).isFalse();
        assertThat(service.getStatus(anna.getId(), pirosCoach).submissionOpen()).isFalse();
        assertThat(service.getStatus(anna.getId(), kekAdmin).submissionOpen()).isTrue();
        assertThat(service.getStatus(anna.getId(), kekCoach).submissionOpen()).isTrue();
        assertThat(service.getStatus(anna.getId(), parent).submissionOpen()).isTrue();

        when(compliance.isClubReady(kek)).thenReturn(false);
        assertThat(service.getStatus(anna.getId(), parent).submissionOpen()).isFalse();
    }

    @Test
    void aClubAdminsFormNamesOnlyItsOwnClubWhileTheParentsNamesEveryReadyOne() {
        when(compliance.isClubReady(piros)).thenReturn(true);
        pirosTeam.setMedicalEvidence(MedicalEvidence.REDACTED_FILE);

        MedicalStatusResponse parentView = service.getStatus(anna.getId(), parent);
        assertThat(parentView.notices()).extracting(MedicalNoticeItem::clubId).containsExactly(kek.getId(), piros.getId());
        assertThat(parentView.fileRequired()).isTrue();

        // Kék FC's admin enters for Kék FC only: Piros SE's notice and file request are not its business.
        MedicalStatusResponse kekView = service.getStatus(anna.getId(), kekAdmin);
        assertThat(kekView.notices()).extracting(MedicalNoticeItem::clubId).containsExactly(kek.getId());
        assertThat(kekView.fileRequired()).isFalse();
        assertThat(kekView.submissionOpen()).isTrue();
    }

    @Test
    void aClubAdminSeesOnlyTheHistoryRowsMeantForItsClub() {
        when(compliance.isClubReady(piros)).thenReturn(true);
        MedicalCertificate forKek = row(List.of(kek.getId()));
        forKek.setSupersededAt(NOW.minusSeconds(60));
        MedicalCertificate forPiros = row(List.of(piros.getId()));
        forPiros.setSupersededAt(NOW.minusSeconds(30));
        when(certificateRepository.findHistoryByProfileId(anna.getId())).thenReturn(List.of(forPiros, forKek));
        current = row(List.of(kek.getId(), piros.getId()));

        assertThat(service.getStatus(anna.getId(), kekAdmin).history()).hasSize(1)
                .first().satisfies(item -> assertThat(item.supersededAt()).isEqualTo(forKek.getSupersededAt()));
        assertThat(service.getStatus(anna.getId(), pirosAdmin).history()).hasSize(1)
                .first().satisfies(item -> assertThat(item.supersededAt()).isEqualTo(forPiros.getSupersededAt()));
        assertThat(service.getStatus(anna.getId(), parent).history()).hasSize(2);
    }

    @Test
    void aCoachWhoseClubAsksForTheDateOnlyIsNotOfferedAnUploadAndChecksThePaper() {
        // Piros SE asks for the redacted copy, Kék FC for the date only; the row has a file and both see it.
        when(compliance.isClubReady(piros)).thenReturn(true);
        pirosTeam.setMedicalEvidence(MedicalEvidence.REDACTED_FILE);
        current = row(List.of(kek.getId(), piros.getId()));
        current.setObjectKey("medical/" + anna.getId() + "/" + current.getId());

        assertThat(service.getStatus(anna.getId(), kekCoach).fileRequired()).isFalse();
        assertThat(service.getStatus(anna.getId(), pirosCoach).fileRequired()).isTrue();

        // Kék FC's coach checks the paper, as its menu shows; the file is Piros SE's business.
        service.verify(anna.getId(), new MedicalVerificationRequest("VERSENYEZHET", UNTIL, "ORIGINAL"), kekCoach);
        assertThat(current.getVerificationMethod()).isEqualTo(MedicalCertificate.VerificationMethod.ORIGINAL);
        service.unverify(anna.getId(), kekCoach);

        // Piros SE's coach has no file access, so it cannot verify this row at all.
        assertCode(() -> service.verify(anna.getId(), new MedicalVerificationRequest("VERSENYEZHET", UNTIL, "FILE"), pirosCoach),
                "error.auth.forbidden");
    }

    @Test
    void aClubAdminsStatusReadsTheRosterAFewTimesHoweverLongTheHistory() {
        when(compliance.isClubReady(piros)).thenReturn(true);
        List<MedicalCertificate> history = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            MedicalCertificate old = row(List.of(i % 2 == 0 ? kek.getId() : piros.getId()));
            old.setSupersededAt(NOW.minusSeconds(1_000 + i));
            history.add(old);
        }
        when(certificateRepository.findHistoryByProfileId(anna.getId())).thenReturn(history);
        current = row(List.of(kek.getId()));
        org.mockito.Mockito.clearInvocations(teamRosterRepository);

        assertThat(service.getStatus(anna.getId(), kekAdmin).history()).hasSize(10);

        // Each access check reads the roster once (it was 10 before, and more per row before that), never per row.
        int reads = org.mockito.Mockito.mockingDetails(teamRosterRepository).getInvocations().size();
        assertThat(reads).isLessThanOrEqualTo(8);
    }

    // ---------------------------------------------------------------- share

    @Test
    void theParentSharesTheCertificateWithAClubThatBecameReadyAndItsVerificationStays() {
        submitByParent();
        service.verify(anna.getId(), new MedicalVerificationRequest("VERSENYEZHET", UNTIL, "ORIGINAL"), kekAdmin);
        when(compliance.isClubReady(piros)).thenReturn(true);
        piros.setMedicalNoticeVersion(5);

        assertThat(service.getStatus(anna.getId(), parent).shareableClubs()).containsExactly(
                new MedicalShareableClubItem(piros.getId(), "Piros SE", "2004. évi I. törvény", "URL", false));
        assertCode(() -> service.share(anna.getId(), current.getId(), new MedicalShareRequest(piros.getId(), false), parent),
                "error.medical.acknowledgement_required");
        assertThat(current.getClubIds()).containsExactly(kek.getId());

        MedicalStatusResponse shared = service.share(anna.getId(), current.getId(), new MedicalShareRequest(piros.getId(), true), parent);

        assertThat(current.getClubIds()).containsExactly(kek.getId(), piros.getId());
        assertThat(shared.sharedWith()).extracting(MedicalClubItem::clubId).containsExactly(kek.getId(), piros.getId());
        assertThat(shared.shareableClubs()).isEmpty();
        verify(auditEventService).record(eq("MEDICAL_CERTIFICATE_SHARED"), eq(parent.getId()), eq("MEDICAL_CERTIFICATE"),
                eq(current.getId().toString()), eq("profileId=" + anna.getId() + ",clubId=" + piros.getId() + ",notice=" + piros.getId() + ":5"));
        MedicalStatusResponse pirosView = service.getStatus(anna.getId(), pirosAdmin);
        assertThat(pirosView.status()).isEqualTo("VALID");
        assertThat(pirosView.verified()).isTrue();
        assertThat(pirosView.verifiedByRole()).isEqualTo("CLUB_ADMIN");

        assertCode(() -> service.share(anna.getId(), current.getId(), new MedicalShareRequest(piros.getId(), true), parent),
                "error.medical.share_not_available");
    }

    @Test
    void aClubCannotShareAndOnlyARequiringReadyClubCanBeAdded() {
        submitByParent();
        when(compliance.isClubReady(piros)).thenReturn(true);
        MedicalShareRequest toPiros = new MedicalShareRequest(piros.getId(), true);

        for (User club : List.of(kekAdmin, kekCoach, pirosAdmin, pirosCoach)) {
            assertCode(() -> service.share(anna.getId(), current.getId(), toPiros, club), "error.auth.forbidden");
        }
        assertCode(() -> service.share(anna.getId(), current.getId(), new MedicalShareRequest(UUID.randomUUID(), true), parent),
                "error.medical.share_not_available");
        assertCode(() -> service.share(anna.getId(), current.getId(), new MedicalShareRequest(null, true), parent),
                "error.medical.share_not_available");
        assertCode(() -> service.share(anna.getId(), UUID.randomUUID(), toPiros, parent), "error.medical.not_current");

        when(compliance.isClubReady(piros)).thenReturn(false);
        assertCode(() -> service.share(anna.getId(), current.getId(), toPiros, parent), "error.medical.share_not_available");
        assertThat(current.getClubIds()).containsExactly(kek.getId());
    }

    @Test
    void aClubThatAsksForTheFileGetsOnlyARowWithAFile() {
        submitByParent();
        when(compliance.isClubReady(piros)).thenReturn(true);
        pirosTeam.setMedicalEvidence(MedicalEvidence.REDACTED_FILE);

        assertThat(service.getStatus(anna.getId(), parent).shareableClubs())
                .extracting(MedicalShareableClubItem::needsFile).containsExactly(true);
        assertCode(() -> service.share(anna.getId(), current.getId(), new MedicalShareRequest(piros.getId(), true), parent),
                "error.medical.share_needs_file");

        current.setObjectKey("medical/" + anna.getId() + "/" + current.getId());
        service.share(anna.getId(), current.getId(), new MedicalShareRequest(piros.getId(), true), parent);
        assertThat(current.getClubIds()).containsExactly(kek.getId(), piros.getId());
    }

    @Test
    void theAdultPlayerSharesItsOwnCertificate() {
        anna.setDateOfBirth(LocalDate.of(2000, 1, 1));
        User player = user(UserRole.PLAYER);
        anna.setLinkedUser(player);
        current = row(List.of(kek.getId()));
        when(compliance.isClubReady(piros)).thenReturn(true);

        service.share(anna.getId(), current.getId(), new MedicalShareRequest(piros.getId(), true), player);

        assertThat(current.getClubIds()).containsExactly(kek.getId(), piros.getId());
    }

    // ---------------------------------------------------------------- in person

    @Test
    void anInPersonEntryGoesOnlyToTheEnteringClub() {
        when(compliance.isClubReady(piros)).thenReturn(true);

        service.submitInPerson(anna.getId(), new MedicalInPersonRequest("VERSENYEZHET", UNTIL, true), kekCoach);

        assertThat(current.getClubIds()).containsExactly(kek.getId());
        assertThat(current.isVerified()).isTrue();
        assertThat(service.getStatus(anna.getId(), pirosAdmin).status()).isEqualTo("MISSING");
        assertThat(service.getStatus(anna.getId(), parent).shareableClubs())
                .extracting(MedicalShareableClubItem::clubId).containsExactly(piros.getId());
    }

    @Test
    void aClubsEntryReplacesTheSharedCertificateForEveryClubThatSawIt() {
        submitByParent();
        when(compliance.isClubReady(piros)).thenReturn(true);
        service.share(anna.getId(), current.getId(), new MedicalShareRequest(piros.getId(), true), parent);
        MedicalCertificate shared = current;

        // Kék FC's coach records a ban from the paper: Piros SE gets it too, nobody is left with the old one.
        service.submitInPerson(anna.getId(), new MedicalInPersonRequest("IDEIGLENESEN_ELTILTVA", UNTIL, true), kekCoach);

        assertThat(shared.getSupersededAt()).isEqualTo(NOW);
        assertThat(current).isNotSameAs(shared);
        assertThat(current.getClubIds()).containsExactly(kek.getId(), piros.getId());
        assertThat(service.getStatus(anna.getId(), pirosAdmin).status()).isEqualTo("SUSPENDED");
        assertThat(service.getStatus(anna.getId(), kekAdmin).status()).isEqualTo("SUSPENDED");
    }

    @Test
    void aRowMeantOnlyForAnotherClubIsReplacedForItTooButItsDetailsStayHidden() {
        // Only Piros SE sees the current certificate, a file valid for long; Kék FC is ready and sees nothing.
        when(compliance.isClubReady(piros)).thenReturn(true);
        pirosTeam.setMedicalEvidence(MedicalEvidence.REDACTED_FILE);
        current = row(List.of(piros.getId()));
        current.setObjectKey("medical/" + anna.getId() + "/" + current.getId());
        current.setValidUntil(LocalDate.of(2027, 10, 1));
        MedicalCertificate pirosRow = current;

        // The file rule refuses a positive entry, without telling Kék FC about Piros SE's certificate.
        assertCode(() -> service.submitInPerson(anna.getId(), new MedicalInPersonRequest("VERSENYEZHET", UNTIL, true), kekCoach),
                MedicalCertificateService.ENTRY_NOT_POSSIBLE);
        assertThat(current).isSameAs(pirosRow);

        // A ban always goes through, and reaches Piros SE as well.
        service.submitInPerson(anna.getId(), new MedicalInPersonRequest("VEGLEGESEN_ELTILTVA", null, true), kekCoach);
        assertThat(current.getClubIds()).containsExactly(kek.getId(), piros.getId());
        assertThat(service.getStatus(anna.getId(), pirosAdmin).status()).isEqualTo("NOT_FIT");
    }

    @Test
    void theParentsNewSubmissionKeepsAClubTheOldOneWasSharedWith() {
        submitByParent();
        when(compliance.isClubReady(piros)).thenReturn(true);
        service.share(anna.getId(), current.getId(), new MedicalShareRequest(piros.getId(), true), parent);
        // Piros SE is not ready for a moment: the new submission goes to Kék FC, but stays meant for Piros SE too.
        when(compliance.isClubReady(piros)).thenReturn(false);

        service.submit(anna.getId(), new SubmitMedicalCertificateRequest("VERSENYEZHET", UNTIL.plusYears(1), true, null, null, null), parent);

        assertThat(current.getClubIds()).containsExactly(kek.getId(), piros.getId());
        when(compliance.isClubReady(piros)).thenReturn(true);
        assertThat(service.getStatus(anna.getId(), pirosAdmin).validUntil()).isEqualTo(UNTIL.plusYears(1));
    }

    @Test
    void aCoachOfBothClubsEntersForBothButNotForOneThatIsNotReady() {
        User both = coach(kekTeam);
        when(teamMembershipQuery.isCoachOnTeam(both.getId(), pirosTeam.getId())).thenReturn(true);

        service.submitInPerson(anna.getId(), new MedicalInPersonRequest("VERSENYEZHET", UNTIL, true), both);
        assertThat(current.getClubIds()).containsExactly(kek.getId());

        when(compliance.isClubReady(piros)).thenReturn(true);
        service.submitInPerson(anna.getId(), new MedicalInPersonRequest("VERSENYEZHET", UNTIL.plusDays(1), true), both);
        assertThat(current.getClubIds()).containsExactly(kek.getId(), piros.getId());

        // Piros SE's coach cannot enter while Piros SE is not ready, even though Kék FC is.
        when(compliance.isClubReady(piros)).thenReturn(false);
        assertCode(() -> service.submitInPerson(anna.getId(), new MedicalInPersonRequest("VERSENYEZHET", UNTIL, true), pirosCoach),
                "error.medical.club_not_ready");
    }

    // ---------------------------------------------------------------- helpers

    private void submitByParent() {
        service.submit(anna.getId(), new SubmitMedicalCertificateRequest("VERSENYEZHET", UNTIL, true, null, null, null), parent);
    }

    private MedicalCertificate keep(MedicalCertificate row) {
        if (row.isCurrent()) {
            current = row;
        } else if (row.isPending()) {
            pending = row;
        }
        return row;
    }

    private void uploadByParent() {
        service.submit(anna.getId(), new SubmitMedicalCertificateRequest("VERSENYEZHET", UNTIL, true, "image/jpeg", 1000L, true), parent);
    }

    private MedicalCertificate row(List<UUID> clubIds) {
        MedicalCertificate row = new MedicalCertificate();
        row.setId(UUID.randomUUID());
        row.setPlayerProfileId(anna.getId());
        row.setResult(MedicalResult.VERSENYEZHET);
        row.setValidUntil(UNTIL);
        row.setEvidence(MedicalCertificate.Evidence.DATE_ONLY);
        row.setSubmittedByRole(MedicalSubmitterRole.PARENT);
        row.setAcknowledgementVersion(MedicalCertificate.ACKNOWLEDGEMENT_DATE);
        row.setCreatedAt(NOW.minusSeconds(3_600));
        row.setCompletedAt(NOW.minusSeconds(3_600));
        row.setClubIds(new ArrayList<>(clubIds));
        return row;
    }

    private static Club club(String name) {
        Club club = new Club();
        club.setId(UUID.randomUUID());
        club.setName(name);
        club.setMedicalMode(MedicalMode.ON);
        club.setMedicalLegalBasis("2004. évi I. törvény");
        club.setMedicalNoticeUrl("https://example.org/" + name);
        club.setMedicalNoticeVersion(2);
        return club;
    }

    private static Team team(Club club) {
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setName(club.getName() + " U12");
        team.setClub(club);
        team.setMedicalMode(MedicalMode.ON);
        return team;
    }

    private TeamRoster roster(Team team) {
        TeamRoster roster = new TeamRoster();
        roster.setId(new TeamRosterId(anna.getId(), team.getId()));
        roster.setTeam(team);
        return roster;
    }

    private static User user(UserRole hat) {
        User user = TestHats.user(hat);
        user.setId(UUID.randomUUID());
        TestHats.grant(user, hat);
        return user;
    }

    private User clubAdmin(Club club) {
        User admin = user(UserRole.CLUB_ADMIN);
        when(userAccessService.hasHat(admin, UserRole.CLUB_ADMIN)).thenReturn(true);
        when(userAccessService.isClubAdmin(admin, club.getId())).thenReturn(true);
        return admin;
    }

    private User coach(Team team) {
        User coach = user(UserRole.COACH);
        when(userAccessService.hasHat(coach, UserRole.COACH)).thenReturn(true);
        when(teamMembershipQuery.isCoachOnTeam(coach.getId(), team.getId())).thenReturn(true);
        return coach;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOfAny(BadRequestException.class, ConflictException.class, ForbiddenException.class, NotFoundException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getMessageCode()).isEqualTo(code));
    }
}
