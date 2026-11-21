package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.ClubMedicalAgreement;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.responses.ClubMedicalAgreementResponse;
import com.example.footballanalysis.model.responses.MedicalFileResponse;
import com.example.footballanalysis.repository.ClubMedicalAgreementRepository;
import com.example.footballanalysis.repository.ClubRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The operator's side of making sport medical collection available to a club (docs/sportorvosi-jogszeruseg.md,
 * Engedélyezés az üzemeltetőtől). Once the club's legal representative signed the data processing agreement, the
 * platform admin uploads the signed PDF here; while it is live and of the current agreement version, the club
 * may turn collection on (it still decides that itself, with its own conditions). Withdrawing it stops new
 * submissions in the club, like a new agreement version does, but deletes nothing.
 * <p>
 * Only the platform admin records and withdraws. The platform admin and the club's own admins read the list and the
 * signed PDF: it is the club's own contract, not health data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MedicalAgreementService {

    static final int REASON_MIN = 10;
    static final int REASON_MAX = 500;
    static final long FILE_MAX_BYTES = 5L * 1024 * 1024;
    private static final String DOWNLOAD_NAME = "adatfeldolgozasi-szerzodes.pdf";

    private final ClubRepository clubRepository;
    private final ClubMedicalAgreementRepository agreementRepository;
    private final ClubDocumentStore documentStore;
    private final MedicalCompliance compliance;
    private final UserAccessService userAccessService;
    private final AuditEventService auditEventService;
    private final Clock clock;

    /**
     * Records the club's signed agreement of the current version and makes collection available to the club. A live
     * earlier agreement ends as REPLACED; its PDF is kept as proof.
     */
    @Transactional
    public ClubMedicalAgreementResponse record(UUID clubId, byte[] pdf, User actor) {
        requireActor(actor);
        requirePlatformAdmin(actor);
        // Locked: two recordings at once would both find no live row and both insert one.
        Club club = loadClubForUpdate(clubId);
        String version = compliance.currentDpaVersion();
        if (version == null) {
            throw new BadRequestException("error.medical.dpa_unavailable", new Object[0],
                    "The data processing agreement is not available yet.");
        }
        if (pdf != null && pdf.length > FILE_MAX_BYTES) {
            throw new BadRequestException("error.medical.agreement_too_large", new Object[0],
                    "The signed agreement can be at most 5 MB.");
        }
        if (pdf == null || pdf.length == 0 || !MedicalCertificateService.startsLike("application/pdf", pdf)) {
            throw new BadRequestException("error.medical.agreement_file_type", new Object[0],
                    "The signed agreement must be a PDF file.");
        }

        Instant now = clock.instant();
        agreementRepository.findFirstByClubIdAndEndedAtIsNull(clubId).ifPresent(previous -> {
            previous.setEndedAt(now);
            previous.setEndedByUserId(actor.getId());
            previous.setEndKind(ClubMedicalAgreement.EndKind.REPLACED);
            // Ended in the database before the new live row, or the one-live-row index refuses the insert.
            agreementRepository.saveAndFlush(previous);
        });

        String key = ClubDocumentStore.agreementKey(clubId);
        documentStore.putPdf(key, pdf);

        ClubMedicalAgreement agreement = new ClubMedicalAgreement();
        agreement.setId(UUID.randomUUID());
        agreement.setClubId(clubId);
        agreement.setClubName(club.getName());
        agreement.setDpaVersion(version);
        agreement.setObjectKey(key);
        agreement.setRecordedAt(now);
        agreement.setRecordedByUserId(actor.getId());
        agreementRepository.save(agreement);

        club.setMedicalAgreementVersion(version);
        clubRepository.save(club);
        auditEventService.record(
                "MEDICAL_AGREEMENT_RECORDED",
                actor.getId(),
                "CLUB",
                clubId.toString(),
                "version=" + version + ",agreementId=" + agreement.getId());
        log.info("Recorded the signed medical agreement {} of club {}, version {}", agreement.getId(), clubId, version);
        return toResponse(agreement);
    }

    /**
     * Ends the club's live agreement. New submissions in the club stop until a new one is recorded; nothing is
     * deleted, and the club's switch stays as the club left it.
     */
    @Transactional
    public void withdraw(UUID clubId, String reasonRaw, User actor) {
        requireActor(actor);
        requirePlatformAdmin(actor);
        Club club = loadClubForUpdate(clubId);
        String reason = reason(reasonRaw);
        ClubMedicalAgreement agreement = agreementRepository.findFirstByClubIdAndEndedAtIsNull(clubId)
                .orElseThrow(MedicalAgreementService::notFound);
        agreement.setEndedAt(clock.instant());
        agreement.setEndedByUserId(actor.getId());
        agreement.setEndKind(ClubMedicalAgreement.EndKind.WITHDRAWN);
        agreement.setWithdrawalReason(reason);
        agreementRepository.save(agreement);

        club.setMedicalAgreementVersion(null);
        clubRepository.save(club);
        auditEventService.record(
                "MEDICAL_AGREEMENT_WITHDRAWN",
                actor.getId(),
                "CLUB",
                clubId.toString(),
                "version=" + agreement.getDpaVersion() + ",agreementId=" + agreement.getId());
        log.info("Withdrew the signed medical agreement {} of club {}", agreement.getId(), clubId);
    }

    /** The club's agreements, the newest first: the platform admin and the club's own admins. */
    @Transactional(readOnly = true)
    public List<ClubMedicalAgreementResponse> list(UUID clubId, User actor) {
        requireActor(actor);
        loadClub(clubId);
        userAccessService.requireClubAdmin(actor, clubId);
        return agreementRepository.findByClubIdOrderByRecordedAtDescIdDesc(clubId).stream()
                .map(MedicalAgreementService::toResponse)
                .toList();
    }

    /** A short-lived link to one signed PDF of the club. */
    @Transactional(readOnly = true)
    public MedicalFileResponse file(UUID clubId, UUID agreementId, User actor) {
        requireActor(actor);
        loadClub(clubId);
        userAccessService.requireClubAdmin(actor, clubId);
        ClubMedicalAgreement agreement = agreementRepository.findByIdAndClubId(agreementId, clubId)
                .orElseThrow(MedicalAgreementService::notFound);
        return new MedicalFileResponse(documentStore.downloadUrl(agreement.getObjectKey(), DOWNLOAD_NAME),
                ClubDocumentStore.DOWNLOAD_URL_TTL.toSeconds());
    }

    private static String reason(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() < REASON_MIN || value.length() > REASON_MAX) {
            throw new BadRequestException("error.medical.agreement_reason", new Object[]{REASON_MIN, REASON_MAX},
                    "Give a reason of " + REASON_MIN + " to " + REASON_MAX + " characters.");
        }
        return value;
    }

    private void requirePlatformAdmin(User actor) {
        if (!userAccessService.isAdmin(actor)) {
            throw new ForbiddenException("error.auth.forbidden", new Object[0],
                    "Only the platform admin records the club's signed agreement.");
        }
    }

    private Club loadClubForUpdate(UUID clubId) {
        return clubRepository.findByIdForUpdate(clubId)
                .orElseThrow(() -> new NotFoundException("error.club.not_found", new Object[]{clubId}, "Club not found: " + clubId));
    }

    private Club loadClub(UUID clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("error.club.not_found", new Object[]{clubId}, "Club not found: " + clubId));
    }

    private static NotFoundException notFound() {
        return new NotFoundException("error.medical.agreement_not_found", new Object[0],
                "The club has no such signed agreement.");
    }

    private static ClubMedicalAgreementResponse toResponse(ClubMedicalAgreement agreement) {
        return new ClubMedicalAgreementResponse(
                agreement.getId(),
                agreement.getDpaVersion(),
                agreement.getRecordedAt(),
                agreement.isActive(),
                agreement.getEndedAt(),
                agreement.getEndKind() == null ? null : agreement.getEndKind().name(),
                agreement.getWithdrawalReason());
    }

    private static void requireActor(User actor) {
        if (actor == null || actor.getId() == null) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required.");
        }
    }
}
