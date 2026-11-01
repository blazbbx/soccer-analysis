package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.MedicalAdminAccess;
import com.example.footballanalysis.model.db.MedicalCertificate;
import com.example.footballanalysis.model.db.MedicalParentSeen;
import com.example.footballanalysis.model.db.MedicalParentSeenId;
import com.example.footballanalysis.model.db.MedicalRejectionReason;
import com.example.footballanalysis.model.db.MedicalResult;
import com.example.footballanalysis.model.db.MedicalSubmitterRole;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.MedicalAdminAccessRequest;
import com.example.footballanalysis.model.requests.MedicalAdminWithdrawalRequest;
import com.example.footballanalysis.model.requests.MedicalInPersonRequest;
import com.example.footballanalysis.model.requests.MedicalDateCorrectionRequest;
import com.example.footballanalysis.model.requests.MedicalRejectionRequest;
import com.example.footballanalysis.model.requests.MedicalVerificationRequest;
import com.example.footballanalysis.model.requests.SetMedicalUploadRightRequest;
import com.example.footballanalysis.model.requests.SubmitMedicalCertificateRequest;
import com.example.footballanalysis.model.responses.MedicalAdminAccessItem;
import com.example.footballanalysis.model.responses.MedicalAdminAccessResponse;
import com.example.footballanalysis.model.responses.ClubMedicalStatusItem;
import com.example.footballanalysis.model.responses.ClubMedicalStatusResponse;
import com.example.footballanalysis.model.responses.MedicalFileResponse;
import com.example.footballanalysis.model.responses.MedicalHistoryItemResponse;
import com.example.footballanalysis.model.responses.MedicalNoticeItem;
import com.example.footballanalysis.model.responses.MedicalStatusResponse;
import com.example.footballanalysis.model.responses.MedicalUploadResponse;
import com.example.footballanalysis.model.responses.MedicalUploadRightResponse;
import com.example.footballanalysis.model.responses.PageResponse;
import com.example.footballanalysis.model.responses.TeamMedicalStatusItem;
import com.example.footballanalysis.model.responses.TeamMedicalStatusResponse;
import com.example.footballanalysis.paging.PageQuery;
import com.example.footballanalysis.repository.AuditEventRepository;
import com.example.footballanalysis.repository.MedicalAdminAccessRepository;
import com.example.footballanalysis.repository.MedicalCertificateRepository;
import com.example.footballanalysis.repository.MedicalParentSeenRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.TeamRosterRepository;
import com.example.footballanalysis.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sport medical certificates of a child (docs/sportorvosi-beadas.md). The file never passes through
 * the application: the browser puts it on a presigned URL, and {@link #complete} checks the stored
 * object before the certificate becomes current. The platform admin has no view of its own: it reads,
 * opens and withdraws only through the break-glass calls, with a reason the club sees
 * (docs/sportorvosi-jogszeruseg.md).
 */
@Service
@Slf4j
public class MedicalCertificateService {

    /** The redaction editor flattens every copy to one JPEG. A PDF could keep its text under the covers, so it is refused. */
    static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg");
    static final long MAX_BYTES = 10_485_760L;
    static final Duration PENDING_TTL = Duration.ofMinutes(20);
    static final int MAX_PENDING = 3;
    static final int ADMIN_REASON_MIN = 20;
    static final int ADMIN_REASON_MAX = 500;
    /** A FILE verification needs the caller's own opening of the file within this time. */
    static final Duration FILE_VERIFICATION_WINDOW = Duration.ofMinutes(30);

    private final MedicalCertificateRepository certificateRepository;
    private final MedicalAdminAccessRepository adminAccessRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final TeamRepository teamRepository;
    private final TeamRosterRepository teamRosterRepository;
    private final TeamMembershipQuery teamMembershipQuery;
    private final UserRepository userRepository;
    private final UserAccessService userAccessService;
    private final MedicalAccessService medicalAccess;
    private final MedicalRequirementService medicalRequirement;
    private final MedicalFileStore fileStore;
    private final MedicalStorageGuard storageGuard;
    private final MedicalCompliance compliance;
    private final AuditEventService auditEventService;
    private final AuditEventRepository auditEventRepository;
    private final MedicalParentSeenRepository parentSeenRepository;
    private final Clock clock;
    private final TransactionTemplate requiresNew;
    private final TransactionTemplate readOnly;

    public MedicalCertificateService(MedicalCertificateRepository certificateRepository,
                                     MedicalAdminAccessRepository adminAccessRepository,
                                     PlayerProfileRepository playerProfileRepository,
                                     TeamRepository teamRepository,
                                     TeamRosterRepository teamRosterRepository,
                                     TeamMembershipQuery teamMembershipQuery,
                                     UserRepository userRepository,
                                     UserAccessService userAccessService,
                                     MedicalAccessService medicalAccess,
                                     MedicalRequirementService medicalRequirement,
                                     MedicalFileStore fileStore,
                                     MedicalStorageGuard storageGuard,
                                     MedicalCompliance compliance,
                                     AuditEventService auditEventService,
                                     AuditEventRepository auditEventRepository,
                                     MedicalParentSeenRepository parentSeenRepository,
                                     Clock clock,
                                     PlatformTransactionManager transactionManager) {
        this.certificateRepository = certificateRepository;
        this.adminAccessRepository = adminAccessRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.teamRepository = teamRepository;
        this.teamRosterRepository = teamRosterRepository;
        this.teamMembershipQuery = teamMembershipQuery;
        this.userRepository = userRepository;
        this.userAccessService = userAccessService;
        this.medicalAccess = medicalAccess;
        this.medicalRequirement = medicalRequirement;
        this.fileStore = fileStore;
        this.storageGuard = storageGuard;
        this.compliance = compliance;
        this.auditEventService = auditEventService;
        this.auditEventRepository = auditEventRepository;
        this.parentSeenRepository = parentSeenRepository;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    // ---------------------------------------------------------------- submit

    /**
     * Starts a submission. Where the child needs no file (docs/sportorvosi-jogszeruseg.md, Bizonyítási mód) the row is
     * current at once and nothing is uploaded; the storage does not matter. Where one of its ON teams asks for the
     * redacted copy, the row is pending until {@link #complete} checked the uploaded file.
     */
    @Transactional
    public MedicalUploadResponse submit(UUID profileId, SubmitMedicalCertificateRequest request, User actor) {
        requireActor(actor);
        PlayerProfile profile = loadProfile(profileId);
        MedicalSubmitterRole role = medicalAccess.submitterRole(actor, profile).orElseThrow(MedicalCertificateService::forbidden);
        requireRequired(profileId);
        List<Club> clubs = requiringClubs(profileId);
        requireClubsReady(clubs);
        if (request == null || !Boolean.TRUE.equals(request.acknowledged())) {
            throw new BadRequestException("error.medical.acknowledgement_required", new Object[0],
                    "Confirm the notice before uploading.");
        }
        MedicalResult result = parseResult(request.result(), request.validUntil());
        boolean fileRequired = medicalRequirement.fileRequired(profileId);
        if (role == MedicalSubmitterRole.COACH && !fileRequired) {
            throw new BadRequestException("error.medical.in_person_only", new Object[0],
                    "Here enter the result and the date with the in-person entry.");
        }
        if (fileRequired) {
            validateFile(request);
            storageGuard.requireSecure();
        } else if (request.contentType() != null || request.sizeBytes() != null) {
            throw new BadRequestException("error.medical.file_not_requested", new Object[0],
                    "The club asks for the result and the date only. Do not upload a file.");
        }

        playerProfileRepository.findByIdForUpdate(profileId).orElseThrow(() -> profileNotFound(profileId));
        Instant now = clock.instant();
        requireNoPastReplacement(profileId, result, request.validUntil());
        if (role == MedicalSubmitterRole.COACH) {
            requireMayReplaceFile(profileId, result, request.validUntil());
        }

        if (!fileRequired) {
            MedicalCertificate certificate = newRow(profileId, result, request.validUntil(), actor, role, now,
                    MedicalCertificate.Evidence.DATE_ONLY, MedicalCertificate.ACKNOWLEDGEMENT_DATE);
            makeCurrent(certificate, now);
            auditEventService.record(
                    "MEDICAL_CERTIFICATE_SUBMITTED",
                    actor.getId(),
                    roleName(actor),
                    "MEDICAL_CERTIFICATE",
                    certificate.getId().toString(),
                    "profileId=" + profileId + ",evidence=DATE_ONLY,notices=" + noticeVersions(clubs));
            log.info("Recorded date-only medical certificate {} for profile {}", certificate.getId(), profileId);
            return new MedicalUploadResponse(certificate.getId(), null, null, true);
        }

        List<MedicalFileStore.KeyRef> stale = new ArrayList<>();
        int fresh = 0;
        for (MedicalCertificate pending : certificateRepository.findPendingByProfileId(profileId)) {
            if (pending.getCreatedAt().isBefore(now.minus(PENDING_TTL))) {
                stale.add(new MedicalFileStore.KeyRef(pending.getObjectKey(), pending.getId()));
                certificateRepository.delete(pending);
            } else {
                fresh++;
            }
        }
        fileStore.deleteAfterCommit(stale);
        if (fresh >= MAX_PENDING) {
            throw new ConflictException("error.medical.too_many_pending", new Object[0],
                    "Too many unfinished uploads. Try again in a few minutes.");
        }

        MedicalCertificate certificate = newRow(profileId, result, request.validUntil(), actor, role, now,
                MedicalCertificate.Evidence.FILE, MedicalCertificate.ACKNOWLEDGEMENT_FILE);
        certificate.setObjectKey(MedicalFileStore.objectKey(profileId, certificate.getId()));
        certificate.setContentType(request.contentType());
        certificate.setSizeBytes(request.sizeBytes());
        certificateRepository.save(certificate);

        String uploadUrl = fileStore.uploadUrl(certificate.getObjectKey(), request.contentType(), request.sizeBytes());
        log.info("Started medical certificate {} for profile {}", certificate.getId(), profileId);
        return new MedicalUploadResponse(certificate.getId(), uploadUrl, MedicalFileStore.UPLOAD_URL_TTL.toSeconds(), false);
    }

    private static MedicalCertificate newRow(UUID profileId, MedicalResult result, LocalDate validUntil, User actor,
                                             MedicalSubmitterRole role, Instant now, MedicalCertificate.Evidence evidence,
                                             String acknowledgementVersion) {
        MedicalCertificate certificate = new MedicalCertificate();
        certificate.setId(UUID.randomUUID());
        certificate.setPlayerProfileId(profileId);
        certificate.setResult(result);
        certificate.setValidUntil(validUntil);
        certificate.setEvidence(evidence);
        certificate.setSubmittedByUserId(actor.getId());
        certificate.setSubmittedByRole(role);
        certificate.setAcknowledgedAt(now);
        certificate.setAcknowledgementVersion(acknowledgementVersion);
        certificate.setCreatedAt(now);
        return certificate;
    }

    /**
     * Supersedes the current row and makes this one current. The old row is flushed first, or the partial unique
     * index sees two current rows; its file is deleted after the commit.
     */
    private void makeCurrent(MedicalCertificate certificate, Instant now) {
        certificateRepository.findCurrentByProfileId(certificate.getPlayerProfileId()).ifPresent(old -> {
            String oldKey = old.getObjectKey();
            old.setSupersededAt(now);
            old.setObjectKey(null);
            certificateRepository.saveAndFlush(old);
            fileStore.deleteAfterCommit(oldKey, old.getId());
        });
        certificate.setCompletedAt(now);
        certificateRepository.save(certificate);
    }

    /**
     * The result, and the date it needs: the three dated results require one, NEM_ALKALMAS and VEGLEGESEN_ELTILTVA
     * must come without.
     */
    static MedicalResult parseResult(String raw, LocalDate validUntil) {
        MedicalResult result;
        try {
            result = raw == null ? null : MedicalResult.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            result = null;
        }
        if (result == null) {
            throw new BadRequestException("validation.medical.result", new Object[0], "Choose the result shown on the certificate.");
        }
        if (result.hasDate() && validUntil == null) {
            throw new BadRequestException("validation.medical.valid_until", new Object[0], "The date is required.");
        }
        if (!result.hasDate() && validUntil != null) {
            throw new BadRequestException("validation.medical.valid_until_not_allowed", new Object[0],
                    "This result has no date. Leave the date empty.");
        }
        return result;
    }

    /**
     * A past positive date is accepted, but it must not supersede a certificate that is still good and delete its
     * file. A negative result always may: it is fresh information. An old row without a result counts as good while
     * its date has not passed.
     */
    private void requireNoPastReplacement(UUID profileId, MedicalResult result, LocalDate validUntil) {
        LocalDate today = today();
        if (!result.isPositive() || !validUntil.isBefore(today)) {
            return;
        }
        boolean currentStillGood = certificateRepository.findCurrentByProfileId(profileId)
                .map(current -> statusOf(current).isStillGood()
                        || (current.getResult() == null && current.getValidUntil() != null && !current.getValidUntil().isBefore(today)))
                .orElse(false);
        if (currentStillGood) {
            throw new BadRequestException("validation.medical.valid_until_past_replace", new Object[0],
                    "An expired certificate cannot replace one that is still valid.");
        }
    }

    private static void validateFile(SubmitMedicalCertificateRequest request) {
        if (!Boolean.TRUE.equals(request.redactionConfirmed())) {
            throw new BadRequestException("error.medical.redaction_required", new Object[0],
                    "Confirm that you covered the personal details on the copy.");
        }
        if (request.contentType() == null || !ALLOWED_TYPES.contains(request.contentType())) {
            throw new BadRequestException("error.medical.file_type", new Object[0],
                    "Only the JPEG made by the redaction editor can be uploaded.");
        }
        if (request.sizeBytes() == null || request.sizeBytes() < 1 || request.sizeBytes() > MAX_BYTES) {
            throw new BadRequestException("error.medical.file_too_large", new Object[0],
                    "The file must be between 1 byte and 10 MiB.");
        }
    }

    // ---------------------------------------------------------------- complete

    /**
     * Not {@code @Transactional}. The work runs in a REQUIRES_NEW template that returns a result instead of
     * throwing, so a rejected file's pending row is deleted and committed before the 400 is thrown here.
     */
    public MedicalStatusResponse complete(UUID profileId, UUID certificateId, User actor) {
        requireActor(actor);
        CompleteOutcome outcome = requiresNew.execute(status -> completeInTransaction(profileId, certificateId, actor));
        if (outcome == null) {
            throw new IllegalStateException("Medical completion returned no outcome.");
        }
        if (outcome.rejectionCode() != null) {
            if (outcome.rejectedKey() != null) {
                fileStore.delete(outcome.rejectedKey(), certificateId);
            }
            String fallback = switch (outcome.rejectionCode()) {
                case "error.medical.file_missing" -> "The uploaded file was not found. Start the upload again.";
                case "error.medical.upload_expired" -> "The upload took too long. Start it again.";
                case "error.medical.file_not_requested" -> "The club asks for the result and the date only. Do not upload a file.";
                case "error.medical.replaced_meanwhile" -> "A newer certificate was recorded while this upload was running.";
                case "validation.medical.valid_until_past_replace" -> "An expired certificate cannot replace one that is still valid.";
                case "error.medical.replace_too_early" -> "The uploaded certificate is valid for more than 30 days. The parent or the club admin can replace it.";
                case "error.medical.replace_not_later" -> "The new date must be later than the date of the current certificate.";
                default -> "The uploaded file does not match the declared type or size.";
            };
            throw new BadRequestException(outcome.rejectionCode(), new Object[0], fallback);
        }
        return readOnly.execute(status -> buildStatus(loadProfile(profileId), actor));
    }

    private CompleteOutcome completeInTransaction(UUID profileId, UUID certificateId, User actor) {
        PlayerProfile profile = playerProfileRepository.findByIdForUpdate(profileId)
                .orElseThrow(() -> profileNotFound(profileId));
        MedicalCertificate certificate = certificateRepository.findById(certificateId)
                .filter(found -> profileId.equals(found.getPlayerProfileId()))
                .filter(MedicalCertificate::isPending)
                .orElseThrow(() -> new NotFoundException("error.medical.not_found", new Object[0],
                        "Medical certificate upload not found."));
        MedicalSubmitterRole role = medicalAccess.submitterRole(actor, profile).orElseThrow(MedicalCertificateService::forbidden);
        requireRequired(profileId);
        // A new agreement version, or a notice removed meanwhile, stops the submission here as well.
        List<Club> clubs = requiringClubs(profileId);
        requireClubsReady(clubs);

        // Past the TTL the row counts as stale everywhere else (submit and the cleanup delete it), so it cannot turn current here.
        if (certificate.getCreatedAt().isBefore(clock.instant().minus(PENDING_TTL))) {
            String staleKey = certificate.getObjectKey();
            certificateRepository.delete(certificate);
            return CompleteOutcome.rejected(staleKey, "error.medical.upload_expired");
        }

        // The club may have stopped asking for the file since the upload started. No file clock runs for this row,
        // so turning it current would leave a file nobody asked for and the cleanup does not see.
        if (!medicalRequirement.fileRequired(profileId)) {
            String unaskedKey = certificate.getObjectKey();
            certificateRepository.delete(certificate);
            return CompleteOutcome.rejected(unaskedKey, "error.medical.file_not_requested");
        }

        // The current row may have changed in the up to 20 minutes since submit checked it. Turning this row current
        // supersedes that row and deletes its file, so the rules run again here, under the profile lock.
        String refusal = replacementRefusal(profileId, certificate, role);
        if (refusal != null) {
            String refusedKey = certificate.getObjectKey();
            certificateRepository.delete(certificate);
            return CompleteOutcome.rejected(refusedKey, refusal);
        }

        // A storage outage throws out of here: the transaction rolls back, the pending row stays and the
        // caller gets a 503 it can retry. Only a file MinIO reports missing or one that does not match is dropped.
        String key = certificate.getObjectKey();
        Optional<Long> size = fileStore.size(key);
        if (size.isEmpty()) {
            certificateRepository.delete(certificate);
            return CompleteOutcome.rejected(null, "error.medical.file_missing");
        }
        boolean sizeMatches = size.get().equals(certificate.getSizeBytes()) && size.get() <= MAX_BYTES;
        Optional<byte[]> head = sizeMatches ? fileStore.firstBytes(key, 8) : Optional.empty();
        if (sizeMatches && head.isEmpty()) {
            certificateRepository.delete(certificate);
            return CompleteOutcome.rejected(null, "error.medical.file_missing");
        }
        // startsLike also knows the PDF, for the club's notice. A certificate must still be an allowed type here.
        boolean matches = sizeMatches && ALLOWED_TYPES.contains(certificate.getContentType()) && startsLike(certificate.getContentType(), head.get());
        if (!matches) {
            certificateRepository.delete(certificate);
            return CompleteOutcome.rejected(key, "error.medical.file_mismatch");
        }

        makeCurrent(certificate, clock.instant());
        auditEventService.record(
                "MEDICAL_CERTIFICATE_SUBMITTED",
                actor.getId(),
                roleName(actor),
                "MEDICAL_CERTIFICATE",
                certificate.getId().toString(),
                "profileId=" + profileId + ",evidence=FILE,notices=" + noticeVersions(clubs));
        log.info("Completed medical certificate {} for profile {}", certificate.getId(), profileId);
        return CompleteOutcome.accepted();
    }

    /**
     * Why this pending row must not turn current, or null. A positive upload started before the current row was recorded
     * is older news and must not overwrite it; a negative result may, as it may at submit. The rest are submit's rules:
     * no past date over a good certificate, and a coach replaces a file only when it is due.
     */
    private String replacementRefusal(UUID profileId, MedicalCertificate pending, MedicalSubmitterRole role) {
        MedicalResult result = pending.getResult();
        if (result == null) {
            return null;
        }
        boolean newerCurrent = certificateRepository.findCurrentByProfileId(profileId)
                .map(MedicalCertificate::getCompletedAt)
                .filter(completedAt -> completedAt.isAfter(pending.getCreatedAt()))
                .isPresent();
        if (result.isPositive() && newerCurrent) {
            return "error.medical.replaced_meanwhile";
        }
        try {
            requireNoPastReplacement(profileId, result, pending.getValidUntil());
            if (role == MedicalSubmitterRole.COACH || pending.getSubmittedByRole() == MedicalSubmitterRole.COACH) {
                requireMayReplaceFile(profileId, result, pending.getValidUntil());
            }
            return null;
        } catch (BadRequestException | ConflictException refused) {
            return refused.getMessageCode();
        }
    }

    static boolean startsLike(String contentType, byte[] bytes) {
        if (bytes == null) {
            return false;
        }
        return switch (contentType) {
            case "application/pdf" -> startsWith(bytes, 0x25, 0x50, 0x44, 0x46);
            case "image/jpeg" -> startsWith(bytes, 0xFF, 0xD8, 0xFF);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private record CompleteOutcome(String rejectedKey, String rejectionCode) {
        static CompleteOutcome accepted() {
            return new CompleteOutcome(null, null);
        }

        static CompleteOutcome rejected(String key, String code) {
            return new CompleteOutcome(key, code);
        }
    }

    // ---------------------------------------------------------------- withdraw, open

    @Transactional
    public void withdraw(UUID profileId, UUID certificateId, User actor) {
        requireActor(actor);
        PlayerProfile profile = loadProfile(profileId);
        if (medicalAccess.managerRole(actor, profileId).isEmpty() && !medicalAccess.isAdultLinkedPlayer(actor, profile)) {
            throw forbidden();
        }
        withdrawCurrent(profileId, certificateId, actor, "MEDICAL_CERTIFICATE_WITHDRAWN");
    }

    private MedicalCertificate withdrawCurrent(UUID profileId, UUID certificateId, User actor, String eventType) {
        playerProfileRepository.findByIdForUpdate(profileId).orElseThrow(() -> profileNotFound(profileId));
        MedicalCertificate certificate = certificateRepository.findById(certificateId)
                .filter(found -> profileId.equals(found.getPlayerProfileId()))
                .filter(MedicalCertificate::isCurrent)
                .orElseThrow(MedicalCertificateService::notCurrent);
        String key = certificate.getObjectKey();
        certificate.setWithdrawnAt(clock.instant());
        certificate.setObjectKey(null);
        certificateRepository.save(certificate);
        fileStore.deleteAfterCommit(key, certificate.getId());
        auditEventService.record(
                eventType,
                actor.getId(),
                roleName(actor),
                "MEDICAL_CERTIFICATE",
                certificate.getId().toString(),
                "profileId=" + profileId);
        log.info("Withdrew medical certificate {} of profile {}", certificate.getId(), profileId);
        return certificate;
    }

    @Transactional(readOnly = true)
    public MedicalFileResponse openFile(UUID profileId, UUID certificateId, User actor) {
        requireActor(actor);
        PlayerProfile profile = loadProfile(profileId);
        // In the grace only those who may also withdraw the file open it: no coach, club admin or minor player.
        boolean allowed = medicalRequirement.isRequired(profileId) ? canOpenFile(actor, profile) : managesLeftover(actor, profile);
        if (!allowed) {
            throw forbidden();
        }
        MedicalCertificate certificate = certificateRepository.findById(certificateId)
                .filter(found -> profileId.equals(found.getPlayerProfileId()))
                .filter(MedicalCertificate::isCurrent)
                .filter(found -> found.getObjectKey() != null)
                .orElseThrow(MedicalCertificateService::notCurrent);
        String url = fileStore.downloadUrl(certificate.getObjectKey(), certificate.getContentType());
        auditEventService.record(
                "MEDICAL_CERTIFICATE_OPENED",
                actor.getId(),
                roleName(actor),
                "MEDICAL_CERTIFICATE",
                certificate.getId().toString(),
                "");
        log.info("Opened medical certificate {} of profile {}", certificate.getId(), profileId);
        return new MedicalFileResponse(url, MedicalFileStore.DOWNLOAD_URL_TTL.toSeconds());
    }

    // ---------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public MedicalStatusResponse getStatus(UUID profileId, User actor) {
        requireActor(actor);
        return buildStatus(loadProfile(profileId), actor);
    }

    @Transactional(readOnly = true)
    public MedicalStatusResponse getMyStatus(User actor) {
        requireActor(actor);
        PlayerProfile profile = playerProfileRepository.findByLinkedUser_Id(actor.getId())
                .orElseThrow(() -> new NotFoundException("error.player_profile.not_found", new Object[]{actor.getId()},
                        "No player profile is linked to this account."));
        if (!medicalRequirement.isRequired(profile.getId())) {
            MedicalStatusResponse notRequired = notRequiredStatus(profile, actor);
            // The section needs the profile to open or withdraw the leftover file.
            return notRequired.certificateId() == null ? notRequired : notRequired.toBuilder().profileId(profile.getId()).build();
        }
        boolean adult = !medicalAccess.isMinor(profile);
        MedicalStatusResponse status = adult || profile.isCanUploadMedical()
                ? fullStatus(profile, true) : readOnlyStatus(profile);
        return withClublessAccesses(status, profile.getId(), adult).toBuilder()
                .profileId(profile.getId())
                .adult(adult)
                .parentAccessAllowed(adult ? profile.isMedicalParentAccessAllowed() : null)
                .build();
    }

    /**
     * {@link #statusFor}, plus the platform admin's accesses that no club sees: to the parent of a minor (or of an adult
     * who allowed it), and to the adult player. The parent also learns whether the state is unread for it.
     */
    private MedicalStatusResponse buildStatus(PlayerProfile profile, User actor) {
        MedicalStatusResponse status = statusFor(profile, actor);
        boolean parent = medicalAccess.isParent(actor, profile.getId());
        if (!parent && !medicalAccess.isAdultLinkedPlayer(actor, profile)) {
            return status;
        }
        status = withClublessAccesses(status, profile.getId(), true);
        if (!parent || !status.required()) {
            return status;
        }
        MedicalCertificate current = certificateRepository.findCurrentByProfileId(profile.getId()).orElse(null);
        MedicalParentSeen seen = parentSeenRepository.findById(new MedicalParentSeenId(profile.getId(), actor.getId())).orElse(null);
        List<MedicalAdminAccess> clubless = adminAccessRepository.findWithoutClubByProfileId(profile.getId());
        return status.toBuilder().unseen(isUnseen(actor.getId(), current, seen, clubless)).build();
    }

    private MedicalStatusResponse withClublessAccesses(MedicalStatusResponse status, UUID profileId, boolean shown) {
        if (!shown) {
            return status;
        }
        List<MedicalAdminAccessItem> unseenByClubs = adminAccessRepository.findWithoutClubByProfileId(profileId).stream()
                .map(row -> new MedicalAdminAccessItem(null, row.getCreatedAt(), null, null, null, row.getAction().name(), row.getReason()))
                .toList();
        return unseenByClubs.isEmpty() ? status : status.withAdminAccesses(unseenByClubs);
    }

    /**
     * Unread for this parent: the current row came from someone else and the parent has not looked at this row yet,
     * a verifier corrected its date since the last look, or a break-glass access shown to the parent is newer than the
     * last look. Without a current row there is
     * nothing to acknowledge, so nothing is unread.
     */
    static boolean isUnseen(UUID parentId, MedicalCertificate current, MedicalParentSeen seen, List<MedicalAdminAccess> clubless) {
        if (current == null) {
            return false;
        }
        boolean newRow = !parentId.equals(current.getSubmittedByUserId())
                && (seen == null || !current.getId().equals(seen.getCertificateId()));
        Instant seenAt = seen != null ? seen.getSeenAt() : null;
        boolean newAccess = clubless.stream().anyMatch(access -> seenAt == null || access.getCreatedAt().isAfter(seenAt));
        boolean newCorrection = current.getDateCorrectedAt() != null
                && (seenAt == null || current.getDateCorrectedAt().isAfter(seenAt));
        return newRow || newAccess || newCorrection;
    }

    /**
     * The parent opened the child's profile and saw the current certificate. A separate POST, because the reading GET
     * does not write. Only the current row can be acknowledged.
     */
    @Transactional
    public void markSeen(UUID profileId, UUID certificateId, User actor) {
        requireActor(actor);
        loadProfile(profileId);
        if (!medicalAccess.isParent(actor, profileId)) {
            throw forbidden();
        }
        MedicalCertificate current = certificateRepository.findCurrentByProfileId(profileId).orElse(null);
        if (current == null || !current.getId().equals(certificateId)) {
            throw new ConflictException("error.medical.not_current", new Object[0], "This is not the current medical certificate.");
        }
        MedicalParentSeenId id = new MedicalParentSeenId(profileId, actor.getId());
        MedicalParentSeen seen = parentSeenRepository.findById(id).orElseGet(() -> {
            MedicalParentSeen created = new MedicalParentSeen();
            created.setId(id);
            return created;
        });
        seen.setCertificateId(certificateId);
        seen.setSeenAt(clock.instant());
        parentSeenRepository.save(seen);
    }

    private MedicalStatusResponse statusFor(PlayerProfile profile, User actor) {
        UUID profileId = profile.getId();
        boolean full = medicalAccess.managerRole(actor, profileId).isPresent()
                || medicalAccess.isUploadingPlayer(actor, profile);
        // The child always sees its own data, read only (GDPR 15): without the upload right only the file is closed.
        boolean ownReadOnly = !full && medicalAccess.isLinkedPlayer(actor, profile);
        boolean datesOnly = !full && !ownReadOnly && medicalAccess.isCoachRequiringProfile(actor, profileId);
        if (!full && !ownReadOnly && !datesOnly) {
            throw forbidden();
        }
        if (!medicalRequirement.isRequired(profileId)) {
            return notRequiredStatus(profile, actor);
        }
        if (ownReadOnly) {
            return readOnlyStatus(profile);
        }
        if (datesOnly) {
            return coachStatus(profileId, actor);
        }
        return fullStatus(profile, canOpenFile(actor, profile));
    }

    /** The parent and the adult player: the only ones who reach the file left over in the 30-day grace. */
    private boolean managesLeftover(User actor, PlayerProfile profile) {
        return medicalAccess.isParent(actor, profile.getId()) || medicalAccess.isAdultLinkedPlayer(actor, profile);
    }

    /** Not required: nothing, or for those who manage it, the leftover file to open or withdraw. */
    private MedicalStatusResponse notRequiredStatus(PlayerProfile profile, User actor) {
        if (managesLeftover(actor, profile)) {
            Optional<MedicalCertificate> leftover = certificateRepository.findCurrentByProfileId(profile.getId())
                    .filter(current -> current.getObjectKey() != null);
            if (leftover.isPresent()) {
                return MedicalStatusResponse.builder().required(false).certificateId(leftover.get().getId()).hasFile(true).build();
            }
        }
        return MedicalStatusResponse.notRequired();
    }

    /**
     * The coach's narrow state: the light, the result and the verification, no submitter, history or notices. The
     * certificate id and the file flag only where the club lets this coach open the file.
     */
    private MedicalStatusResponse coachStatus(UUID profileId, User actor) {
        MedicalCertificate current = certificateRepository.findCurrentByProfileId(profileId).orElse(null);
        MedicalStatus status = statusOf(current);
        User verifier = current != null && current.getVerifiedByUserId() != null
                ? userRepository.findById(current.getVerifiedByUserId()).orElse(null) : null;
        boolean fileAccess = current != null && medicalAccess.isCoachWithFileAccess(actor, profileId);
        return MedicalStatusResponse.builder()
                .required(true)
                .status(status.status().name())
                .result(resultName(current))
                .resultAllowsTraining(status.resultAllowsTraining())
                .resultAllowsCompeting(status.resultAllowsCompeting())
                .validUntil(current != null ? current.getValidUntil() : null)
                .daysRemaining(status.daysRemaining())
                .certificateId(fileAccess ? current.getId() : null)
                .hasFile(fileAccess ? current.hasFile() : null)
                .fileRequired(medicalRequirement.fileRequired(profileId))
                .verified(current != null && current.isVerified())
                .verifiedAt(current != null ? current.getVerifiedAt() : null)
                .verifiedByFirstName(verifier != null ? verifier.getFirstName() : null)
                .verifiedByLastName(verifier != null ? verifier.getLastName() : null)
                .verifiedByRole(current != null && current.getVerifiedByRole() != null ? current.getVerifiedByRole().name() : null)
                .verificationMethod(current != null && current.getVerificationMethod() != null ? current.getVerificationMethod().name() : null)
                .coachFileAccess(fileAccess)
                .build();
    }

    /** The child's own state without the upload right: everything it may know, but nothing that targets the file. */
    private MedicalStatusResponse readOnlyStatus(PlayerProfile profile) {
        return fullStatus(profile, false).toBuilder().certificateId(null).hasFile(null).canUploadMedical(false).build();
    }

    /**
     * The parent, the club admin of a club that asks for the file, the coach where that club allows it, and the child
     * with the upload right.
     */
    private boolean canOpenFile(User actor, PlayerProfile profile) {
        return medicalAccess.isParent(actor, profile.getId())
                || medicalAccess.isClubAdminRequiringFile(actor, profile.getId())
                || medicalAccess.isCoachWithFileAccess(actor, profile.getId())
                || medicalAccess.isUploadingPlayer(actor, profile);
    }

    /** The light of the current row, or MISSING. An unverified positive row is UNVERIFIED. */
    private MedicalStatus statusOf(MedicalCertificate current) {
        return current == null
                ? MedicalStatus.of(false, null, null, false, today())
                : MedicalStatus.of(true, current.getResult(), current.getValidUntil(), current.isVerified(), today());
    }

    private static String resultName(MedicalCertificate row) {
        return row != null && row.getResult() != null ? row.getResult().name() : null;
    }

    /** {@code fileVisible}: the caller may open the file, so {@code hasFile} tells it whether there is one to open. */
    private MedicalStatusResponse fullStatus(PlayerProfile profile, boolean fileVisible) {
        UUID profileId = profile.getId();
        Optional<MedicalCertificate> current = certificateRepository.findCurrentByProfileId(profileId);
        List<MedicalCertificate> history = certificateRepository.findHistoryByProfileId(profileId);
        List<UUID> userIds = new ArrayList<>();
        current.map(MedicalCertificate::getSubmittedByUserId).ifPresent(userIds::add);
        current.map(MedicalCertificate::getVerifiedByUserId).ifPresent(userIds::add);
        history.stream().map(MedicalCertificate::getSubmittedByUserId).filter(Objects::nonNull).forEach(userIds::add);
        Map<UUID, User> users = userIds.isEmpty() ? Map.of() : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));

        LocalDate validUntil = current.map(MedicalCertificate::getValidUntil).orElse(null);
        MedicalStatus status = statusOf(current.orElse(null));
        boolean fileRequired = medicalRequirement.fileRequired(profileId);
        User submittedBy = current.map(MedicalCertificate::getSubmittedByUserId).map(users::get).orElse(null);
        User verifiedBy = current.map(MedicalCertificate::getVerifiedByUserId).map(users::get).orElse(null);
        List<Team> requiringTeams = medicalRequirement.requiringTeams(profileId);
        boolean coachFileAccess = requiringTeams.stream().anyMatch(team -> MedicalRequirementService.asksForFile(team)
                && team.getClub() != null && team.getClub().isMedicalCoachFileAccess());
        List<MedicalHistoryItemResponse> items = history.stream().map(row -> {
            User by = row.getSubmittedByUserId() == null ? null : users.get(row.getSubmittedByUserId());
            return new MedicalHistoryItemResponse(
                    resultName(row),
                    row.getValidUntil(),
                    row.getCompletedAt(),
                    by != null ? by.getFirstName() : null,
                    by != null ? by.getLastName() : null,
                    row.getSubmittedByRole().name(),
                    row.getSupersededAt(),
                    row.getWithdrawnAt(),
                    row.getVerifiedAt(),
                    row.getRejectedAt());
        }).toList();
        // Shown until a new row replaces it: a rejection leaves no current row, and the newest history row tells why.
        MedicalCertificate rejected = current.isEmpty() && !history.isEmpty() && history.get(0).getRejectedAt() != null
                ? history.get(0) : null;
        return MedicalStatusResponse.builder()
                .required(true)
                .status(status.status().name())
                .result(resultName(current.orElse(null)))
                .resultAllowsTraining(status.resultAllowsTraining())
                .resultAllowsCompeting(status.resultAllowsCompeting())
                .validUntil(validUntil)
                .daysRemaining(status.daysRemaining())
                .certificateId(current.map(MedicalCertificate::getId).orElse(null))
                .hasFile(fileVisible && current.map(MedicalCertificate::hasFile).orElse(false))
                .submittedAt(current.map(MedicalCertificate::getCompletedAt).orElse(null))
                .submittedByFirstName(submittedBy != null ? submittedBy.getFirstName() : null)
                .submittedByLastName(submittedBy != null ? submittedBy.getLastName() : null)
                .submittedByRole(current.map(row -> row.getSubmittedByRole().name()).orElse(null))
                .history(items)
                .canUploadMedical(profile.isCanUploadMedical())
                .notices(requiringClubs(profileId).stream().map(MedicalCertificateService::noticeItem).toList())
                .fileRequired(fileRequired)
                .fileRequestedNext(fileRequired && current.isPresent() && !current.get().hasFile())
                .verified(current.map(MedicalCertificate::isVerified).orElse(false))
                .verifiedAt(current.map(MedicalCertificate::getVerifiedAt).orElse(null))
                .verifiedByFirstName(verifiedBy != null ? verifiedBy.getFirstName() : null)
                .verifiedByLastName(verifiedBy != null ? verifiedBy.getLastName() : null)
                .verifiedByRole(current.map(MedicalCertificate::getVerifiedByRole).map(Enum::name).orElse(null))
                .verificationMethod(current.map(MedicalCertificate::getVerificationMethod).map(Enum::name).orElse(null))
                .coachFileAccess(coachFileAccess)
                .rejectionReason(rejected != null ? rejected.getRejectionReason().name() : null)
                .rejectedAt(rejected != null ? rejected.getRejectedAt() : null)
                .rejectedByRole(rejected != null ? rejected.getRejectedByRole().name() : null)
                .dateCorrectedAt(current.map(MedicalCertificate::getDateCorrectedAt).orElse(null))
                .dateCorrectedByRole(current.map(MedicalCertificate::getDateCorrectedByRole).map(Enum::name).orElse(null))
                .originalValidUntil(current.map(MedicalCertificate::getOriginalValidUntil).orElse(null))
                .build();
    }

    private static MedicalNoticeItem noticeItem(Club club) {
        return new MedicalNoticeItem(club.getId(), club.getName(), club.getMedicalLegalBasis(), MedicalSettingsService.noticeKind(club));
    }

    /**
     * The light for each child of a parent's list, in a few batched queries. An adult child who did not allow it shows
     * nothing but {@code adultManaged}. {@code parent} may be null: then nothing is unread.
     */
    @Transactional(readOnly = true)
    public Map<UUID, ChildMedical> childSummaries(Collection<PlayerProfile> children, User parent) {
        if (children == null || children.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ChildMedical> result = new HashMap<>();
        List<UUID> profileIds = new ArrayList<>();
        for (PlayerProfile child : children) {
            if (child.getId() == null) {
                continue;
            }
            if (!medicalAccess.isMinor(child) && !child.isMedicalParentAccessAllowed()) {
                result.put(child.getId(), ChildMedical.forAdult());
            } else {
                profileIds.add(child.getId());
            }
        }
        if (profileIds.isEmpty()) {
            return result;
        }
        Set<UUID> required = medicalRequirement.requiredProfileIds(profileIds);
        Map<UUID, MedicalCertificate> current = required.isEmpty() ? Map.of()
                : certificateRepository.findCurrentByProfileIdIn(required).stream()
                        .collect(Collectors.toMap(MedicalCertificate::getPlayerProfileId, Function.identity(), (left, right) -> left));
        Map<UUID, MedicalParentSeen> seen = parent == null || required.isEmpty() ? Map.of()
                : parentSeenRepository.findByParentAndProfileIdIn(parent.getId(), required).stream()
                        .collect(Collectors.toMap(row -> row.getId().getPlayerProfileId(), Function.identity()));
        Map<UUID, List<MedicalAdminAccess>> clubless = parent == null || required.isEmpty() ? Map.of()
                : adminAccessRepository.findWithoutClubByProfileIdIn(required).stream()
                        .collect(Collectors.groupingBy(MedicalAdminAccess::getPlayerProfileId));
        for (UUID profileId : profileIds) {
            if (!required.contains(profileId)) {
                result.put(profileId, new ChildMedical(false, null, null, null, null, null, null, null, false, false));
                continue;
            }
            MedicalCertificate row = current.get(profileId);
            MedicalStatus status = statusOf(row);
            boolean unseen = parent != null
                    && isUnseen(parent.getId(), row, seen.get(profileId), clubless.getOrDefault(profileId, List.of()));
            result.put(profileId, new ChildMedical(true, status.status().name(), resultName(row), status.resultAllowsTraining(),
                    status.resultAllowsCompeting(), row != null ? row.getValidUntil() : null, status.daysRemaining(),
                    row != null && row.isVerified(), unseen, false));
        }
        return result;
    }

    public record ChildMedical(boolean required, String status, String result, Boolean resultAllowsTraining,
                               Boolean resultAllowsCompeting, LocalDate validUntil, Integer daysRemaining, Boolean verified,
                               boolean unseen, boolean adultManaged) {
        static ChildMedical forAdult() {
            return new ChildMedical(false, null, null, null, null, null, null, null, false, true);
        }
    }

    @Transactional(readOnly = true)
    public TeamMedicalStatusResponse teamStatus(UUID teamId, User actor) {
        requireActor(actor);
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found."));
        UUID clubId = team.getClub() != null ? team.getClub().getId() : null;
        boolean allowed = userAccessService.isClubAdmin(actor, clubId)
                || (userAccessService.hasHat(actor, UserRole.COACH) && teamMembershipQuery.isCoachOnTeam(actor.getId(), teamId));
        // A club admin or coach hat on the operator's account opens nothing: it stays the platform admin.
        if (!allowed || medicalAccess.isAdmin(actor)) {
            throw forbidden();
        }
        String mode = team.getMedicalMode().name();
        String evidence = team.getMedicalEvidence().name();
        if (!MedicalRequirementService.isOn(team)) {
            return new TeamMedicalStatusResponse(false, mode, evidence, List.of());
        }
        List<UUID> profileIds = teamRosterRepository.findProfileIdsByTeamId(teamId);
        Map<UUID, MedicalCertificate> current = profileIds.isEmpty() ? Map.of()
                : certificateRepository.findCurrentByProfileIdIn(profileIds).stream()
                        .collect(Collectors.toMap(MedicalCertificate::getPlayerProfileId, Function.identity(), (left, right) -> left));
        List<TeamMedicalStatusItem> items = profileIds.stream().map(profileId -> {
            MedicalCertificate row = current.get(profileId);
            MedicalStatus status = statusOf(row);
            return new TeamMedicalStatusItem(profileId, status.status().name(), resultName(row), status.resultAllowsTraining(),
                    status.resultAllowsCompeting(), row != null ? row.getValidUntil() : null, status.daysRemaining(),
                    row != null && row.isVerified());
        }).toList();
        return new TeamMedicalStatusResponse(true, mode, evidence, items);
    }

    // ---------------------------------------------------------------- verification and in-person entry

    /**
     * Marks the current certificate as checked against the paper (docs/sportorvosi-jogszeruseg.md, Ellenőrzés). Where
     * the child needs a file and the row has one, only FILE is accepted: from the club admin who may open the file, or
     * the coach the club lets open it, and only after the caller opened it within 30 minutes. Otherwise only ORIGINAL,
     * from a club admin or a coach of a requiring club or team. The parent, the player and the platform admin cannot.
     */
    @Transactional
    public MedicalStatusResponse verify(UUID profileId, MedicalVerificationRequest request, User actor) {
        requireActor(actor);
        PlayerProfile profile = loadProfile(profileId);
        playerProfileRepository.findByIdForUpdate(profileId).orElseThrow(() -> profileNotFound(profileId));
        MedicalCertificate current = certificateRepository.findCurrentByProfileId(profileId).orElseThrow(MedicalCertificateService::notCurrent);
        MedicalCertificate.VerificationMethod rowMethod = verificationMethodFor(profileId, current);
        MedicalSubmitterRole role = verifierRole(actor, profileId, rowMethod).orElseThrow(MedicalCertificateService::forbidden);
        if (request == null || parseMethod(request.method()) != rowMethod) {
            throw new BadRequestException("error.medical.verification_method", new Object[0],
                    "This certificate is verified " + (rowMethod == MedicalCertificate.VerificationMethod.FILE
                            ? "from the uploaded file." : "from the original paper."));
        }
        if (current.getResult() == null) {
            throw new ConflictException("error.medical.result_missing", new Object[0],
                    "This certificate has no result. Enter it again in person, or ask for a new submission.");
        }
        if (current.isVerified()) {
            return buildStatus(profile, actor);
        }
        if (!current.getResult().name().equalsIgnoreCase(String.valueOf(request.result()).trim())
                || !Objects.equals(current.getValidUntil(), request.validUntil())) {
            throw new ConflictException("error.medical.verification_changed", new Object[0],
                    "The certificate changed meanwhile. Check the new one.");
        }
        if (rowMethod == MedicalCertificate.VerificationMethod.FILE && !openedRecently(actor, current)) {
            throw new ConflictException("error.medical.file_not_opened", new Object[0],
                    "Open the uploaded file before you mark it as verified.");
        }
        current.setVerifiedAt(clock.instant());
        current.setVerifiedByUserId(actor.getId());
        current.setVerifiedByRole(role);
        current.setVerificationMethod(rowMethod);
        certificateRepository.save(current);
        auditEventService.record(
                "MEDICAL_CERTIFICATE_VERIFIED",
                actor.getId(),
                roleName(actor),
                "MEDICAL_CERTIFICATE",
                current.getId().toString(),
                "profileId=" + profileId + ",method=" + rowMethod);
        log.info("Verified medical certificate {} of profile {} ({})", current.getId(), profileId, rowMethod);
        return buildStatus(profile, actor);
    }

    /**
     * The paper does not support the row: it is withdrawn with a reason from the list and its file is deleted, so a
     * badly redacted copy does not stay. The parent sees the reason and can submit a new one.
     */
    @Transactional
    public MedicalStatusResponse reject(UUID profileId, MedicalRejectionRequest request, User actor) {
        requireActor(actor);
        PlayerProfile profile = loadProfile(profileId);
        playerProfileRepository.findByIdForUpdate(profileId).orElseThrow(() -> profileNotFound(profileId));
        MedicalCertificate current = certificateRepository.findCurrentByProfileId(profileId).orElseThrow(MedicalCertificateService::notCurrent);
        MedicalCertificate.VerificationMethod rowMethod = verificationMethodFor(profileId, current);
        MedicalSubmitterRole role = verifierRole(actor, profileId, rowMethod).orElseThrow(MedicalCertificateService::forbidden);
        if (request == null) {
            throw rejectionReasonInvalid();
        }
        MedicalRejectionReason reason = parseRejectionReason(request.reason());
        requireReviewable(current, rowMethod, actor, request.method(), request.result(), request.validUntil());
        Instant now = clock.instant();
        String key = current.getObjectKey();
        current.setWithdrawnAt(now);
        current.setObjectKey(null);
        current.setRejectedAt(now);
        current.setRejectedByUserId(actor.getId());
        current.setRejectedByRole(role);
        current.setRejectionReason(reason);
        certificateRepository.save(current);
        fileStore.deleteAfterCommit(key, current.getId());
        auditEventService.record(
                "MEDICAL_CERTIFICATE_REJECTED",
                actor.getId(),
                roleName(actor),
                "MEDICAL_CERTIFICATE",
                current.getId().toString(),
                "profileId=" + profileId + ",method=" + rowMethod + ",reason=" + reason);
        log.info("Rejected medical certificate {} of profile {} ({})", current.getId(), profileId, reason);
        return buildStatus(profile, actor);
    }

    /**
     * The result is right, only the date was mistyped: the verifier corrects it from the paper in front of them, which
     * also verifies the row. The first entered date stays, and the parent is told.
     */
    @Transactional
    public MedicalStatusResponse correctDate(UUID profileId, MedicalDateCorrectionRequest request, User actor) {
        requireActor(actor);
        PlayerProfile profile = loadProfile(profileId);
        playerProfileRepository.findByIdForUpdate(profileId).orElseThrow(() -> profileNotFound(profileId));
        MedicalCertificate current = certificateRepository.findCurrentByProfileId(profileId).orElseThrow(MedicalCertificateService::notCurrent);
        MedicalCertificate.VerificationMethod rowMethod = verificationMethodFor(profileId, current);
        MedicalSubmitterRole role = verifierRole(actor, profileId, rowMethod).orElseThrow(MedicalCertificateService::forbidden);
        if (request == null || request.correctedValidUntil() == null) {
            throw new BadRequestException("validation.medical.valid_until", new Object[0], "The date is required.");
        }
        if (current.getResult() == null) {
            throw new ConflictException("error.medical.result_missing", new Object[0],
                    "This certificate has no result. Enter it again in person, or ask for a new submission.");
        }
        if (!current.getResult().hasDate()) {
            throw new BadRequestException("validation.medical.valid_until_not_allowed", new Object[0],
                    "This result has no date. Leave the date empty.");
        }
        requireReviewable(current, rowMethod, actor, request.method(), request.result(), request.validUntil());
        if (request.correctedValidUntil().equals(current.getValidUntil())) {
            throw new BadRequestException("error.medical.date_unchanged", new Object[0], "This is the date already entered.");
        }
        Instant now = clock.instant();
        if (current.getOriginalValidUntil() == null) {
            current.setOriginalValidUntil(current.getValidUntil());
        }
        current.setValidUntil(request.correctedValidUntil());
        current.setDateCorrectedAt(now);
        current.setDateCorrectedByUserId(actor.getId());
        current.setDateCorrectedByRole(role);
        current.setVerifiedAt(now);
        current.setVerifiedByUserId(actor.getId());
        current.setVerifiedByRole(role);
        current.setVerificationMethod(rowMethod);
        certificateRepository.save(current);
        auditEventService.record(
                "MEDICAL_CERTIFICATE_DATE_CORRECTED",
                actor.getId(),
                roleName(actor),
                "MEDICAL_CERTIFICATE",
                current.getId().toString(),
                "profileId=" + profileId + ",method=" + rowMethod);
        log.info("Corrected the date of medical certificate {} of profile {} ({})", current.getId(), profileId, rowMethod);
        return buildStatus(profile, actor);
    }

    /** What rejecting and correcting ask of the caller, as verifying does: the right method, the row on the screen, the file opened. */
    private void requireReviewable(MedicalCertificate current, MedicalCertificate.VerificationMethod rowMethod, User actor,
                                   String method, String result, LocalDate validUntil) {
        if (parseMethod(method) != rowMethod) {
            throw new BadRequestException("error.medical.verification_method", new Object[0],
                    "This certificate is verified " + (rowMethod == MedicalCertificate.VerificationMethod.FILE
                            ? "from the uploaded file." : "from the original paper."));
        }
        if (current.isVerified()) {
            throw new ConflictException("error.medical.already_verified", new Object[0],
                    "This certificate is verified. Take the verification back first.");
        }
        String shown = result == null || result.isBlank() ? null : result.trim().toUpperCase();
        if (!Objects.equals(resultName(current), shown) || !Objects.equals(current.getValidUntil(), validUntil)) {
            throw new ConflictException("error.medical.verification_changed", new Object[0],
                    "The certificate changed meanwhile. Check the new one.");
        }
        if (rowMethod == MedicalCertificate.VerificationMethod.FILE && !openedRecently(actor, current)) {
            throw new ConflictException("error.medical.file_not_opened", new Object[0],
                    "Open the uploaded file first.");
        }
    }

    private static MedicalRejectionReason parseRejectionReason(String raw) {
        try {
            if (raw != null) {
                return MedicalRejectionReason.valueOf(raw.trim().toUpperCase());
            }
        } catch (IllegalArgumentException ex) {
            // Falls through to the same answer as a missing reason.
        }
        throw rejectionReasonInvalid();
    }

    private static BadRequestException rejectionReasonInvalid() {
        return new BadRequestException("error.medical.rejection_reason", new Object[0], "Choose why you reject the certificate.");
    }

    /**
     * Takes back a wrong verification. The caller must be one who could verify the row by its stored method; a fresh
     * opening of the file is not needed.
     */
    @Transactional
    public void unverify(UUID profileId, User actor) {
        requireActor(actor);
        loadProfile(profileId);
        playerProfileRepository.findByIdForUpdate(profileId).orElseThrow(() -> profileNotFound(profileId));
        MedicalCertificate current = certificateRepository.findCurrentByProfileId(profileId).orElseThrow(MedicalCertificateService::notCurrent);
        MedicalCertificate.VerificationMethod stored = current.getVerificationMethod() != null
                ? current.getVerificationMethod() : verificationMethodFor(profileId, current);
        verifierRole(actor, profileId, stored).orElseThrow(MedicalCertificateService::forbidden);
        if (!current.isVerified()) {
            return;
        }
        current.setVerifiedAt(null);
        current.setVerifiedByUserId(null);
        current.setVerifiedByRole(null);
        current.setVerificationMethod(null);
        certificateRepository.save(current);
        auditEventService.record(
                "MEDICAL_CERTIFICATE_UNVERIFIED",
                actor.getId(),
                roleName(actor),
                "MEDICAL_CERTIFICATE",
                current.getId().toString(),
                "profileId=" + profileId + ",method=" + stored);
    }

    /**
     * A coach or a club admin enters the result and the date from the original paper shown in person, in the file mode
     * too: the row is current and verified at once by the same person, and the file stays asked for at the next
     * submission. A current file is replaced only under {@link #requireMayReplaceFile}. The platform admin cannot:
     * it never meets the child.
     */
    @Transactional
    public MedicalStatusResponse submitInPerson(UUID profileId, MedicalInPersonRequest request, User actor) {
        requireActor(actor);
        PlayerProfile profile = loadProfile(profileId);
        MedicalSubmitterRole role = medicalAccess.isClubAdminRequiringProfile(actor, profileId) ? MedicalSubmitterRole.CLUB_ADMIN
                : medicalAccess.isCoachRequiringProfile(actor, profileId) ? MedicalSubmitterRole.COACH : null;
        if (role == null) {
            throw forbidden();
        }
        requireRequired(profileId);
        List<Club> clubs = requiringClubs(profileId);
        requireClubsReady(clubs);
        if (request == null || !Boolean.TRUE.equals(request.seenOriginal())) {
            throw new BadRequestException("error.medical.seen_original_required", new Object[0],
                    "Confirm that you saw the original certificate.");
        }
        MedicalResult result = parseResult(request.result(), request.validUntil());
        playerProfileRepository.findByIdForUpdate(profileId).orElseThrow(() -> profileNotFound(profileId));
        requireNoPastReplacement(profileId, result, request.validUntil());
        if (medicalRequirement.fileRequired(profileId)) {
            requireMayReplaceFile(profileId, result, request.validUntil());
        }
        Instant now = clock.instant();
        MedicalCertificate certificate = newRow(profileId, result, request.validUntil(), actor, role, now,
                MedicalCertificate.Evidence.DATE_ONLY, MedicalCertificate.ACKNOWLEDGEMENT_IN_PERSON);
        certificate.setVerifiedAt(now);
        certificate.setVerifiedByUserId(actor.getId());
        certificate.setVerifiedByRole(role);
        certificate.setVerificationMethod(MedicalCertificate.VerificationMethod.ORIGINAL);
        makeCurrent(certificate, now);
        auditEventService.record(
                "MEDICAL_CERTIFICATE_SUBMITTED",
                actor.getId(),
                roleName(actor),
                "MEDICAL_CERTIFICATE",
                certificate.getId().toString(),
                "profileId=" + profileId + ",evidence=DATE_ONLY,inPerson=true,notices=" + noticeVersions(clubs));
        log.info("Recorded medical certificate {} for profile {} in person", certificate.getId(), profileId);
        return buildStatus(profile, actor);
    }

    /**
     * Where a club asks for the file, a coach's upload or an in-person entry replaces a current file only when it is
     * due: the file expires within {@link MedicalStatus#EXPIRING_WITHIN_DAYS} days and the new date is later. A negative
     * result goes through at any time, or a fresh ban could not be recorded while the old paper still looks valid.
     */
    private void requireMayReplaceFile(UUID profileId, MedicalResult result, LocalDate validUntil) {
        MedicalCertificate current = certificateRepository.findCurrentByProfileId(profileId).filter(MedicalCertificate::hasFile).orElse(null);
        if (current == null || !result.isPositive() || current.getValidUntil() == null) {
            return;
        }
        if (current.getValidUntil().isAfter(today().plusDays(MedicalStatus.EXPIRING_WITHIN_DAYS))) {
            throw new ConflictException("error.medical.replace_too_early", new Object[0],
                    "The uploaded certificate is valid for more than 30 days. The parent or the club admin can replace it.");
        }
        if (!validUntil.isAfter(current.getValidUntil())) {
            throw new ConflictException("error.medical.replace_not_later", new Object[0],
                    "The new date must be later than the date of the current certificate.");
        }
    }

    /** FILE where the child needs a file and the row has one; ORIGINAL otherwise (a date-only row, or a file removed by policy). */
    private MedicalCertificate.VerificationMethod verificationMethodFor(UUID profileId, MedicalCertificate row) {
        return row.hasFile() && medicalRequirement.fileRequired(profileId)
                ? MedicalCertificate.VerificationMethod.FILE : MedicalCertificate.VerificationMethod.ORIGINAL;
    }

    /** Who may verify by this method; the club admin wins when both rules allow it. */
    private Optional<MedicalSubmitterRole> verifierRole(User actor, UUID profileId, MedicalCertificate.VerificationMethod method) {
        if (method == MedicalCertificate.VerificationMethod.FILE) {
            if (medicalAccess.isClubAdminRequiringFile(actor, profileId)) {
                return Optional.of(MedicalSubmitterRole.CLUB_ADMIN);
            }
            return medicalAccess.isCoachWithFileAccess(actor, profileId) ? Optional.of(MedicalSubmitterRole.COACH) : Optional.empty();
        }
        if (medicalAccess.isClubAdminRequiringProfile(actor, profileId)) {
            return Optional.of(MedicalSubmitterRole.CLUB_ADMIN);
        }
        return medicalAccess.isCoachRequiringProfile(actor, profileId) ? Optional.of(MedicalSubmitterRole.COACH) : Optional.empty();
    }

    private static MedicalCertificate.VerificationMethod parseMethod(String raw) {
        try {
            return raw == null ? null : MedicalCertificate.VerificationMethod.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Whether this caller opened this certificate's file within the window. The audit's {@code created_at} is the
     * JVM's local time without a zone, so the cutoff is built the same way.
     */
    private boolean openedRecently(User actor, MedicalCertificate certificate) {
        LocalDateTime since = LocalDateTime.ofInstant(clock.instant().minus(FILE_VERIFICATION_WINDOW), ZoneId.systemDefault());
        return auditEventRepository.existsByActorSince("MEDICAL_CERTIFICATE_OPENED", actor.getId(), certificate.getId().toString(), since);
    }

    // ---------------------------------------------------------------- club list

    public static final List<String> CLUB_FILTERS = List.of("UNVERIFIED", "EXPIRING", "NOT_COMPETING", "BANNED", "RESULT_MISSING", "MISSING");

    /**
     * The club tab's list: every child on an ON team of the club, once, with the light, the result and the
     * verification. Only the club's own admins see it. {@code filter} is one of {@link #CLUB_FILTERS}, or none.
     */
    @Transactional(readOnly = true)
    public ClubMedicalStatusResponse clubStatus(UUID clubId, String filter, UUID teamId, Integer page, Integer size, User actor) {
        requireActor(actor);
        userAccessService.requireOwnClubAdmin(actor, clubId);
        String chosen = filter == null || filter.isBlank() ? null : filter.trim().toUpperCase();
        if (chosen != null && !CLUB_FILTERS.contains(chosen)) {
            throw new BadRequestException("error.validation.invalid_request", new Object[]{"filter"}, "Unknown filter: " + filter);
        }
        List<Team> onTeams = teamRepository.findByClub_Id(clubId).stream()
                .filter(MedicalRequirementService::isOn)
                .sorted(Comparator.comparing(Team::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        Map<UUID, List<UUID>> teamsOf = new LinkedHashMap<>();
        Set<UUID> fileProfiles = new LinkedHashSet<>();
        for (Team team : onTeams) {
            for (UUID profileId : teamRosterRepository.findProfileIdsByTeamId(team.getId())) {
                teamsOf.computeIfAbsent(profileId, id -> new ArrayList<>()).add(team.getId());
                if (MedicalRequirementService.asksForFile(team)) {
                    fileProfiles.add(profileId);
                }
            }
        }
        Map<UUID, PlayerProfile> profiles = teamsOf.isEmpty() ? Map.of() : playerProfileRepository.findAllById(teamsOf.keySet()).stream()
                .collect(Collectors.toMap(PlayerProfile::getId, Function.identity()));
        Map<UUID, MedicalCertificate> current = teamsOf.isEmpty() ? Map.of()
                : certificateRepository.findCurrentByProfileIdIn(teamsOf.keySet()).stream()
                        .collect(Collectors.toMap(MedicalCertificate::getPlayerProfileId, Function.identity(), (left, right) -> left));
        Set<UUID> fileRequired = medicalRequirement.fileRequiredProfileIds(teamsOf.keySet());

        List<ClubMedicalStatusItem> all = teamsOf.entrySet().stream().map(entry -> {
            UUID profileId = entry.getKey();
            PlayerProfile profile = profiles.get(profileId);
            MedicalCertificate row = current.get(profileId);
            MedicalStatus status = statusOf(row);
            boolean fileHere = fileProfiles.contains(profileId);
            return new ClubMedicalStatusItem(
                    profileId,
                    profile != null ? profile.getFirstName() : null,
                    profile != null ? profile.getLastName() : null,
                    entry.getValue(),
                    status.status().name(),
                    resultName(row),
                    status.resultAllowsTraining(),
                    status.resultAllowsCompeting(),
                    row != null ? row.getValidUntil() : null,
                    status.daysRemaining(),
                    row != null && row.isVerified(),
                    row != null ? row.getId() : null,
                    fileHere && row != null ? row.hasFile() : null,
                    fileRequired.contains(profileId));
        }).sorted(Comparator.comparing((ClubMedicalStatusItem item) -> nullToEmpty(item.lastName()), String::compareToIgnoreCase)
                .thenComparing(item -> nullToEmpty(item.firstName()), String::compareToIgnoreCase)).toList();

        // A team outside this club's ON teams matches no child, so the filter cannot reach another club's roster.
        List<ClubMedicalStatusItem> inTeam = teamId == null ? all
                : all.stream().filter(item -> item.teamIds().contains(teamId)).toList();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String name : CLUB_FILTERS) {
            counts.put(name, (int) inTeam.stream().filter(item -> matches(item, name)).count());
        }
        List<ClubMedicalStatusItem> filtered = chosen == null ? inTeam : inTeam.stream().filter(item -> matches(item, chosen)).toList();
        int resolvedPage = PageQuery.page(page);
        int resolvedSize = PageQuery.size(size);
        List<ClubMedicalStatusItem> slice = filtered.stream()
                .skip((long) (resolvedPage - 1) * resolvedSize)
                .limit(resolvedSize)
                .toList();
        List<ClubMedicalStatusResponse.TeamRef> teams = onTeams.stream()
                .map(team -> new ClubMedicalStatusResponse.TeamRef(team.getId(), team.getName())).toList();
        // coach_teams, where the coach's rights come from: an extra-hat coach is not a Coach entity.
        Map<UUID, Long> coachCounts = teamMembershipQuery.countCoachesByTeamIds(onTeams.stream().map(Team::getId).toList());
        List<ClubMedicalStatusResponse.TeamRef> withoutCoach = onTeams.stream()
                .filter(team -> !coachCounts.containsKey(team.getId()))
                .map(team -> new ClubMedicalStatusResponse.TeamRef(team.getId(), team.getName())).toList();
        return new ClubMedicalStatusResponse(withoutCoach, teams, counts,
                PageQuery.envelope(slice, resolvedPage, resolvedSize, filtered.size()));
    }

    /**
     * The club tab's filters. NOT_COMPETING counts rows that are verified or negative and do not allow competing: an
     * unverified row has its own filter, so the two do not overlap.
     */
    static boolean matches(ClubMedicalStatusItem item, String filter) {
        String status = item.status();
        return switch (filter) {
            case "UNVERIFIED" -> "UNVERIFIED".equals(status);
            case "EXPIRING" -> "EXPIRING".equals(status) || "EXPIRED".equals(status);
            case "NOT_COMPETING" -> ("VALID".equals(status) || "EXPIRING".equals(status)) && !Boolean.TRUE.equals(item.resultAllowsCompeting())
                    || "SUSPENDED".equals(status) || "NOT_FIT".equals(status);
            case "BANNED" -> "SUSPENDED".equals(status) || "NOT_FIT".equals(status);
            case "RESULT_MISSING" -> "RESULT_MISSING".equals(status);
            case "MISSING" -> "MISSING".equals(status);
            default -> true;
        };
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    // ---------------------------------------------------------------- platform admin, break-glass

    /**
     * The platform admin's only look: technical facts, enough to troubleshoot and to withdraw, never the result, the
     * validity or the file. Every call needs a reason and writes a row the requiring (or last requiring) clubs see.
     * It does not depend on the requirement, so a child in the 30-day grace can still be looked at.
     */
    @Transactional
    public MedicalAdminAccessResponse adminAccess(UUID profileId, MedicalAdminAccessRequest request, User actor) {
        requireActor(actor);
        requirePlatformAdmin(actor);
        String reason = adminReason(request != null ? request.reason() : null);
        PlayerProfile profile = loadProfile(profileId);
        Optional<MedicalCertificate> current = certificateRepository.findCurrentByProfileId(profileId);
        MedicalAdminAccess access = recordAdminAccess(profile, current.map(MedicalCertificate::getId).orElse(null),
                MedicalAdminAccess.Action.READ, reason, actor);
        auditEventService.record(
                "MEDICAL_STATUS_READ_BY_ADMIN",
                actor.getId(),
                roleName(actor),
                current.isPresent() ? "MEDICAL_CERTIFICATE" : "PLAYER_PROFILE",
                current.map(row -> row.getId().toString()).orElse(profileId.toString()),
                "profileId=" + profileId + ",accessId=" + access.getId());
        log.info("Platform admin {} read the technical medical facts of profile {}", actor.getId(), profileId);
        return new MedicalAdminAccessResponse(
                medicalRequirement.isRequired(profileId),
                medicalRequirement.fileRequired(profileId),
                current.map(MedicalCertificate::getId).orElse(null),
                current.map(MedicalCertificate::hasFile).orElse(false),
                current.map(row -> row.getEvidence().name()).orElse(null),
                current.map(MedicalCertificate::getContentType).orElse(null),
                current.map(MedicalCertificate::getSizeBytes).orElse(null),
                current.map(MedicalCertificate::getCompletedAt).orElse(null),
                current.map(MedicalCertificate::isVerified).orElse(false),
                certificateRepository.findHistoryByProfileId(profileId).size(),
                certificateRepository.findPendingByProfileId(profileId).size(),
                profile.getMedicalNotRequiredSince(),
                profile.getMedicalFileNotRequiredSince());
    }

    /** The parent's withdrawal, done by the platform admin on the club's request, with a reason. */
    @Transactional
    public void adminWithdraw(UUID profileId, MedicalAdminWithdrawalRequest request, User actor) {
        requireActor(actor);
        requirePlatformAdmin(actor);
        String reason = adminReason(request != null ? request.reason() : null);
        if (request.certificateId() == null) {
            throw new BadRequestException("error.validation.invalid_request", new Object[]{"certificateId"},
                    "The certificate id is required.");
        }
        PlayerProfile profile = loadProfile(profileId);
        MedicalCertificate certificate = withdrawCurrent(profileId, request.certificateId(), actor,
                "MEDICAL_CERTIFICATE_WITHDRAWN_BY_ADMIN");
        recordAdminAccess(profile, certificate.getId(), MedicalAdminAccess.Action.WITHDRAWN, reason, actor);
    }

    /** The platform admin's accesses shown to this club, newest first. Only the club's own admins see it. */
    @Transactional(readOnly = true)
    public PageResponse<MedicalAdminAccessItem> clubAdminAccesses(UUID clubId, Integer page, Integer size, User actor) {
        requireActor(actor);
        userAccessService.requireOwnClubAdmin(actor, clubId);
        int resolvedPage = PageQuery.page(page);
        int resolvedSize = PageQuery.size(size);
        Page<MedicalAdminAccess> rows = adminAccessRepository.findByClubId(clubId, PageQuery.of(resolvedPage, resolvedSize));
        List<UUID> adminIds = rows.getContent().stream()
                .map(MedicalAdminAccess::getAdminUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, User> admins = adminIds.isEmpty() ? Map.of() : userRepository.findAllById(adminIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
        List<MedicalAdminAccessItem> items = rows.getContent().stream().map(row -> {
            User admin = row.getAdminUserId() == null ? null : admins.get(row.getAdminUserId());
            return new MedicalAdminAccessItem(
                    row.getId(),
                    row.getCreatedAt(),
                    admin != null ? admin.getFirstName() : null,
                    admin != null ? admin.getLastName() : null,
                    row.getPlayerName(),
                    row.getAction().name(),
                    row.getReason());
        }).toList();
        return PageQuery.envelope(items, resolvedPage, resolvedSize, rows.getTotalElements());
    }

    /**
     * Writes the access row for the clubs where the certificate is required now, or, if there are none
     * (the grace period), for the clubs that last required it. A club with collection switched off is never
     * among them: the row (name, reason) is data about the child too.
     */
    private MedicalAdminAccess recordAdminAccess(PlayerProfile profile, UUID certificateId, MedicalAdminAccess.Action action,
                                                 String reason, User actor) {
        List<UUID> clubIds = new ArrayList<>(medicalRequirement.requiringClubIds(profile.getId()));
        if (clubIds.isEmpty() && profile.getMedicalLastRequiredClubIds() != null) {
            clubIds.addAll(profile.getMedicalLastRequiredClubIds());
        }
        MedicalAdminAccess access = new MedicalAdminAccess();
        access.setId(UUID.randomUUID());
        access.setCertificateId(certificateId);
        access.setPlayerProfileId(profile.getId());
        access.setPlayerName(playerName(profile));
        access.setClubIds(clubIds);
        access.setAdminUserId(actor.getId());
        access.setAction(action);
        access.setReason(reason);
        access.setCreatedAt(clock.instant());
        return adminAccessRepository.save(access);
    }

    /** Family name first, as the Hungarian interface shows it, so the club can read the row after the profile is gone. */
    static String playerName(PlayerProfile profile) {
        String last = profile.getLastName() != null ? profile.getLastName().trim() : "";
        String first = profile.getFirstName() != null ? profile.getFirstName().trim() : "";
        return (last + " " + first).trim();
    }

    private void requirePlatformAdmin(User actor) {
        if (!userAccessService.isAdmin(actor)) {
            throw forbidden();
        }
    }

    private static String adminReason(String raw) {
        String reason = raw != null ? raw.trim() : "";
        if (reason.length() < ADMIN_REASON_MIN || reason.length() > ADMIN_REASON_MAX) {
            throw new BadRequestException("error.medical.admin_reason", new Object[]{ADMIN_REASON_MIN, ADMIN_REASON_MAX},
                    "Give a reason of 20 to 500 characters.");
        }
        return reason;
    }

    // ---------------------------------------------------------------- upload right

    @Transactional
    public MedicalUploadRightResponse setUploadRight(UUID profileId, SetMedicalUploadRightRequest request, User actor) {
        requireActor(actor);
        PlayerProfile profile = loadProfile(profileId);
        if (request == null || request.allowed() == null) {
            throw new BadRequestException("error.validation.invalid_request", new Object[0], "The upload right is required.");
        }
        // Under 18 only the parent decides; from 18 the player manages its own paper and needs no right.
        boolean parent = medicalAccess.isParentOfRecord(actor, profileId);
        if (!parent && !medicalAccess.isClubAdminRequiringProfile(actor, profileId)) {
            throw forbidden();
        }
        if (!medicalAccess.isMinor(profile)) {
            throw new ConflictException("error.medical.upload_right_adult", new Object[0],
                    "An adult player manages the certificate without an upload right.");
        }
        if (!parent) {
            throw new ForbiddenException("error.medical.upload_right_parent_only", new Object[0],
                    "Only a parent can set the child's upload right.");
        }
        profile.setCanUploadMedical(request.allowed());
        playerProfileRepository.save(profile);
        auditEventService.record(
                "MEDICAL_UPLOAD_RIGHT_SET",
                actor.getId(),
                roleName(actor),
                "PLAYER_PROFILE",
                profileId.toString(),
                "profileId=" + profileId + ",allowed=" + request.allowed());
        log.info("Set medical upload right for profile {} to {}", profileId, request.allowed());
        return new MedicalUploadRightResponse(profile.isCanUploadMedical());
    }

    /** The adult player lets the parents see its sport medical data, or takes it back. */
    @Transactional
    public MedicalUploadRightResponse setParentAccess(SetMedicalUploadRightRequest request, User actor) {
        requireActor(actor);
        PlayerProfile profile = playerProfileRepository.findByLinkedUser_Id(actor.getId())
                .orElseThrow(() -> new NotFoundException("error.player_profile.not_found", new Object[]{actor.getId()},
                        "No player profile is linked to this account."));
        if (request == null || request.allowed() == null) {
            throw new BadRequestException("error.validation.invalid_request", new Object[0], "The choice is required.");
        }
        if (medicalAccess.isMinor(profile)) {
            throw new ConflictException("error.medical.parent_access_minor", new Object[0],
                    "Under 18 the parents see the sport medical data anyway.");
        }
        profile.setMedicalParentAccessAllowed(request.allowed());
        playerProfileRepository.save(profile);
        auditEventService.record(
                "MEDICAL_PARENT_ACCESS_SET",
                actor.getId(),
                roleName(actor),
                "PLAYER_PROFILE",
                profile.getId().toString(),
                "profileId=" + profile.getId() + ",allowed=" + request.allowed());
        return new MedicalUploadRightResponse(profile.isMedicalParentAccessAllowed());
    }

    // ---------------------------------------------------------------- helpers

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private void requireRequired(UUID profileId) {
        if (!medicalRequirement.isRequired(profileId)) {
            throw new ForbiddenException("error.medical.not_required", new Object[0],
                    "This club does not ask for a sport medical certificate.");
        }
    }

    /** The clubs of the profile's ON teams, each once, in roster order. */
    private List<Club> requiringClubs(UUID profileId) {
        Map<UUID, Club> clubs = new LinkedHashMap<>();
        for (Team team : medicalRequirement.requiringTeams(profileId)) {
            if (team.getClub() != null && team.getClub().getId() != null) {
                clubs.putIfAbsent(team.getClub().getId(), team.getClub());
            }
        }
        return List.copyOf(clubs.values());
    }

    /**
     * A new submission needs every requiring club to be ready (docs/sportorvosi-jogszeruseg.md). Reading, opening,
     * withdrawing and verifying go on regardless: existing data must stay manageable and deletable.
     */
    private void requireClubsReady(List<Club> clubs) {
        if (clubs.stream().anyMatch(club -> !compliance.isClubReady(club))) {
            throw new ForbiddenException("error.medical.club_not_ready", new Object[0],
                    "The club cannot collect sport medical data yet.");
        }
    }

    /** {@code <clubId>:<noticeVersion>,...}: which notices the submitter was shown. */
    static String noticeVersions(List<Club> clubs) {
        return clubs.stream().map(club -> club.getId() + ":" + club.getMedicalNoticeVersion()).collect(Collectors.joining(","));
    }

    private PlayerProfile loadProfile(UUID profileId) {
        return playerProfileRepository.findById(profileId).orElseThrow(() -> profileNotFound(profileId));
    }

    private static NotFoundException profileNotFound(UUID profileId) {
        return new NotFoundException("error.player_profile.not_found", new Object[]{profileId}, "Player profile not found.");
    }

    private static NotFoundException notCurrent() {
        return new NotFoundException("error.medical.not_current", new Object[0],
                "This is not the current medical certificate.");
    }

    private static ForbiddenException forbidden() {
        return new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied.");
    }

    private static void requireActor(User actor) {
        if (actor == null || actor.getId() == null) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required.");
        }
    }

    static String roleName(User actor) {
        return actor != null && actor.getRole() != null ? actor.getRole().name() : null;
    }
}
