package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ConflictWithPropertiesException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.MedicalCertificate;
import com.example.footballanalysis.model.db.MedicalEvidence;
import com.example.footballanalysis.model.db.MedicalMode;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.requests.UpdateClubMedicalSettingsRequest;
import com.example.footballanalysis.model.requests.UpdateTeamMedicalModeRequest;
import com.example.footballanalysis.model.responses.ClubMedicalNoticeResponse;
import com.example.footballanalysis.model.responses.ClubMedicalSettingsResponse;
import com.example.footballanalysis.model.responses.TeamMedicalStatusResponse;
import com.example.footballanalysis.repository.ClubRepository;
import com.example.footballanalysis.repository.MedicalCertificateRepository;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.TeamRosterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Club and team switches for sport medical collection. A club starts OFF and can be turned ON only with
 * the acknowledgement that the law requires it, and only once it is ready (docs/sportorvosi-jogszeruseg.md):
 * it named the law, has a privacy notice, and accepted the current processing agreement. Turning a club or
 * team OFF deletes the file of each child whose last ON team this was, after the caller confirms the count.
 * Children still required elsewhere keep it. Switching the evidence from REDACTED_FILE to DATE_ONLY deletes only the
 * file of each child that no longer needs one; the row with its result and date stays current. Asking for files at
 * all needs a secure storage; the date-only mode does not.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MedicalSettingsService {

    private static final Set<Integer> RETENTION_YEARS = Set.of(1, 3, 5);
    static final int LEGAL_BASIS_MIN = 10;
    static final int LEGAL_BASIS_MAX = 500;
    static final int NOTICE_URL_MAX = 1000;
    static final long NOTICE_MAX_BYTES = 5L * 1024 * 1024;

    private final ClubRepository clubRepository;
    private final TeamRepository teamRepository;
    private final TeamRosterRepository teamRosterRepository;
    private final MedicalCertificateRepository certificateRepository;
    private final MedicalRequirementService medicalRequirement;
    private final MedicalCertificateService medicalCertificateService;
    private final MedicalFileStore fileStore;
    private final ClubDocumentStore documentStore;
    private final MedicalStorageGuard storageGuard;
    private final MedicalCompliance compliance;
    private final UserAccessService userAccessService;
    private final AuditEventService auditEventService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ClubMedicalSettingsResponse getClubSettings(UUID clubId, User actor) {
        requireActor(actor);
        Club club = loadClub(clubId);
        // Not child data, so the platform admin may read it for support. Only writing needs the club's own admin.
        userAccessService.requireClubAdmin(actor, clubId);
        return toResponse(club);
    }

    @Transactional
    public ClubMedicalSettingsResponse updateClubSettings(UUID clubId, UpdateClubMedicalSettingsRequest request, User actor) {
        requireActor(actor);
        Club club = loadClub(clubId);
        // The club decides as the controller of the data: the platform admin cannot write this for it.
        userAccessService.requireOwnClubAdmin(actor, clubId);
        if (request == null) {
            throw new BadRequestException("error.validation.invalid_request", new Object[0], "Settings are required.");
        }
        MedicalMode mode = request.mode() == null ? club.getMedicalMode() : parseMode(request.mode(), false);
        Integer retention = request.historyRetentionYears();
        if (retention != null && !RETENTION_YEARS.contains(retention)) {
            throw new BadRequestException("error.medical.invalid_retention", new Object[0],
                    "The history can be kept for 1, 3 or 5 years.");
        }
        String legalBasis = request.legalBasis() == null ? null : legalBasis(request.legalBasis());
        String noticeUrl = request.noticeUrl() == null ? null : noticeUrl(request.noticeUrl());
        String dpaVersion = request.acceptDpaVersion() == null ? null : acceptedDpaVersion(request.acceptDpaVersion());
        MedicalEvidence evidence = request.evidence() == null ? club.getMedicalEvidence() : parseEvidence(request.evidence(), false);
        boolean turningOn = mode == MedicalMode.ON && club.getMedicalMode() != MedicalMode.ON;
        if (turningOn && !Boolean.TRUE.equals(request.acknowledged())) {
            throw new BadRequestException("error.medical.acknowledgement_required", new Object[0],
                    "Confirm that the law requires the certificate before turning it on.");
        }

        // The conditions are written first, so one request can complete them and turn collection on.
        Instant now = clock.instant();
        if (legalBasis != null && !legalBasis.equals(club.getMedicalLegalBasis())) {
            club.setMedicalLegalBasis(legalBasis);
        }
        if (noticeUrl != null && !noticeUrl.equals(club.getMedicalNoticeUrl())) {
            String oldKey = club.getMedicalNoticeObjectKey();
            club.setMedicalNoticeUrl(noticeUrl);
            club.setMedicalNoticeObjectKey(null);
            bumpNotice(club, now);
            documentStore.deleteAfterCommit(oldKey);
            recordNotice(actor, club, "MEDICAL_NOTICE_SET", "URL");
        }
        boolean coachAccessOn = Boolean.TRUE.equals(request.coachFileAccess()) && !club.isMedicalCoachFileAccess();
        if (coachAccessOn) {
            if (!Boolean.TRUE.equals(request.coachFileAccessAcknowledged())) {
                throw new BadRequestException("error.medical.acknowledgement_required", new Object[0],
                        "Confirm that the club's privacy notice names the coaches among those who may see the copy.");
            }
            boolean fileMode = evidence == MedicalEvidence.REDACTED_FILE || teamRepository.findByClub_Id(clubId).stream()
                    .anyMatch(team -> MedicalRequirementService.effectiveEvidence(team.getMedicalEvidence(), evidence) == MedicalEvidence.REDACTED_FILE);
            if (!fileMode) {
                throw new BadRequestException("error.medical.coach_file_access_without_file", new Object[0],
                        "Coaches can open copies only where the club or one of its teams asks for the redacted copy.");
            }
        }
        if (dpaVersion != null) {
            club.setMedicalDpaVersion(dpaVersion);
            club.setMedicalDpaAcceptedAt(now);
            club.setMedicalDpaAcceptedByUserId(actor.getId());
            auditEventService.record(
                    "MEDICAL_DPA_ACCEPTED",
                    actor.getId(),
                    MedicalCertificateService.roleName(actor),
                    "CLUB",
                    clubId.toString(),
                    "version=" + dpaVersion);
        }
        if (turningOn) {
            requireReady(club);
        }
        // Files are collected from now on: the club switched to the redacted copy, or turned on with it, or one of its
        // teams starts asking for it. The date-only mode does not depend on the storage.
        boolean startsFiles = evidence == MedicalEvidence.REDACTED_FILE
                && (club.getMedicalEvidence() != MedicalEvidence.REDACTED_FILE || turningOn);
        for (Team team : teamRepository.findByClub_Id(clubId)) {
            boolean after = MedicalRequirementService.effectiveMode(team.getMedicalMode(), mode) == MedicalMode.ON
                    && MedicalRequirementService.effectiveEvidence(team.getMedicalEvidence(), evidence) == MedicalEvidence.REDACTED_FILE;
            startsFiles |= after && !MedicalRequirementService.asksForFile(team);
        }
        if (startsFiles) {
            storageGuard.requireSecure();
        }

        List<UUID> profileIds = teamRosterRepository.findProfileIdsByClubId(clubId);
        Set<UUID> requiredBefore = medicalRequirement.requiredProfileIds(profileIds);
        Set<UUID> fileBefore = medicalRequirement.fileRequiredProfileIds(profileIds);
        Map<UUID, Set<UUID>> clubsBefore = medicalRequirement.requiringClubIdsNow(requiredBefore);
        club.setMedicalMode(mode);
        club.setMedicalEvidence(evidence);
        if (turningOn) {
            club.setMedicalAcknowledgedAt(now);
            club.setMedicalAcknowledgedByUserId(actor.getId());
        }
        if (retention != null) {
            club.setMedicalHistoryRetentionYears(retention);
        }
        if (coachAccessOn) {
            club.setMedicalCoachFileAccess(true);
            club.setMedicalCoachFileAccessAcknowledgedAt(now);
            club.setMedicalCoachFileAccessAcknowledgedByUserId(actor.getId());
        } else if (Boolean.FALSE.equals(request.coachFileAccess())) {
            // Takes effect at once; no file is deleted.
            club.setMedicalCoachFileAccess(false);
        }
        clubRepository.save(club);
        removeFilesOfDroppedProfiles(profileIds, requiredBefore, fileBefore, request.confirmDeletion(), actor);
        medicalRequirement.recalculate(profileIds, clubsBefore);

        auditEventService.record(
                "MEDICAL_SETTINGS_SET",
                actor.getId(),
                MedicalCertificateService.roleName(actor),
                "CLUB",
                clubId.toString(),
                "mode=" + mode + ",evidence=" + evidence + ",historyRetentionYears=" + club.getMedicalHistoryRetentionYears()
                        + (turningOn ? ",acknowledged=true" : "")
                        + ",coachFileAccess=" + club.isMedicalCoachFileAccess() + (coachAccessOn ? ",coachFileAccessAcknowledged=true" : ""));
        log.info("Set medical mode of club {} to {}", clubId, mode);
        return toResponse(club);
    }

    @Transactional
    public TeamMedicalStatusResponse updateTeamMode(UUID teamId, UpdateTeamMedicalModeRequest request, User actor) {
        requireActor(actor);
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found."));
        UUID clubId = team.getClub() != null ? team.getClub().getId() : null;
        userAccessService.requireOwnClubAdmin(actor, clubId);
        if (request == null || (request.mode() == null && request.evidence() == null)) {
            throw new BadRequestException("error.medical.invalid_mode", new Object[0], "The mode is required.");
        }
        MedicalMode mode = request.mode() == null ? team.getMedicalMode() : parseMode(request.mode(), true);
        MedicalEvidence evidence = request.evidence() == null ? team.getMedicalEvidence() : parseEvidence(request.evidence(), true);
        // A team turned ON in a club that is not ON starts collection on its own, so it needs the same
        // acknowledgement as turning the club on. In an ON club the club admin has already given it.
        boolean clubOn = team.getClub() != null && team.getClub().getMedicalMode() == MedicalMode.ON;
        boolean turningOn = mode == MedicalMode.ON && !MedicalRequirementService.isOn(team);
        boolean needsAcknowledgement = turningOn && !clubOn;
        if (needsAcknowledgement && !Boolean.TRUE.equals(request.acknowledged())) {
            throw new BadRequestException("error.medical.acknowledgement_required", new Object[0],
                    "Confirm that the law requires the certificate before turning it on.");
        }
        // INHERIT in an ON club starts collection as well, so the checks look at the resulting mode.
        // The club's conditions count even when only the team is ON: the club is the controller.
        MedicalMode clubMode = team.getClub() != null ? team.getClub().getMedicalMode() : MedicalMode.OFF;
        MedicalEvidence clubEvidence = team.getClub() != null ? team.getClub().getMedicalEvidence() : MedicalEvidence.DATE_ONLY;
        boolean onAfter = MedicalRequirementService.effectiveMode(mode, clubMode) == MedicalMode.ON;
        boolean startsCollecting = !MedicalRequirementService.isOn(team) && onAfter;
        boolean startsFiles = !MedicalRequirementService.asksForFile(team) && onAfter
                && MedicalRequirementService.effectiveEvidence(evidence, clubEvidence) == MedicalEvidence.REDACTED_FILE;
        if (startsCollecting) {
            requireReady(team.getClub());
        }
        if (startsFiles) {
            storageGuard.requireSecure();
        }

        List<UUID> profileIds = teamRosterRepository.findProfileIdsByTeamId(teamId);
        Set<UUID> requiredBefore = medicalRequirement.requiredProfileIds(profileIds);
        Set<UUID> fileBefore = medicalRequirement.fileRequiredProfileIds(profileIds);
        Map<UUID, Set<UUID>> clubsBefore = medicalRequirement.requiringClubIdsNow(requiredBefore);
        team.setMedicalMode(mode);
        team.setMedicalEvidence(evidence);
        teamRepository.save(team);
        removeFilesOfDroppedProfiles(profileIds, requiredBefore, fileBefore, request.confirmDeletion(), actor);
        medicalRequirement.recalculate(profileIds, clubsBefore);

        auditEventService.record(
                "MEDICAL_SETTINGS_SET",
                actor.getId(),
                MedicalCertificateService.roleName(actor),
                "TEAM",
                teamId.toString(),
                "mode=" + mode + ",evidence=" + evidence + (needsAcknowledgement ? ",acknowledged=true" : ""));
        log.info("Set medical mode of team {} to {}", teamId, mode);
        return medicalCertificateService.teamStatus(teamId, actor);
    }

    // ---------------------------------------------------------------- privacy notice

    /**
     * Replaces the club's notice with an uploaded PDF (at most 5 MiB, starting with {@code %PDF}). The link, if any,
     * is cleared, the version goes up, and the old PDF is deleted after the commit. The new object is removed again
     * if the transaction rolls back.
     */
    @Transactional
    public ClubMedicalSettingsResponse uploadNotice(UUID clubId, byte[] pdf, User actor) {
        requireActor(actor);
        Club club = loadClub(clubId);
        userAccessService.requireOwnClubAdmin(actor, clubId);
        if (pdf != null && pdf.length > NOTICE_MAX_BYTES) {
            throw new BadRequestException("error.medical.notice_too_large", new Object[0],
                    "The privacy notice can be at most 5 MB.");
        }
        if (pdf == null || pdf.length == 0 || !MedicalCertificateService.startsLike("application/pdf", pdf)) {
            throw new BadRequestException("error.medical.notice_file_type", new Object[0],
                    "The privacy notice must be a PDF file.");
        }
        String key = ClubDocumentStore.noticeKey(clubId);
        documentStore.putPdf(key, pdf);
        String oldKey = club.getMedicalNoticeObjectKey();
        club.setMedicalNoticeObjectKey(key);
        club.setMedicalNoticeUrl(null);
        bumpNotice(club, clock.instant());
        clubRepository.save(club);
        documentStore.deleteAfterCommit(oldKey);
        recordNotice(actor, club, "MEDICAL_NOTICE_SET", "FILE");
        log.info("Uploaded the medical privacy notice of club {}, version {}", clubId, club.getMedicalNoticeVersion());
        return toResponse(club);
    }

    /** Removes the link or the PDF. Not while a team of the club collects: there the notice can only be replaced. */
    @Transactional
    public void removeNotice(UUID clubId, User actor) {
        requireActor(actor);
        Club club = loadClub(clubId);
        userAccessService.requireOwnClubAdmin(actor, clubId);
        if (!MedicalCompliance.hasNotice(club)) {
            throw noticeNotFound();
        }
        boolean collecting = teamRepository.findByClub_Id(clubId).stream().anyMatch(MedicalRequirementService::isOn);
        if (collecting) {
            throw new ConflictException("error.medical.notice_in_use", new Object[0],
                    "The privacy notice cannot be removed while a team of the club collects certificates. Replace it instead.");
        }
        String kind = noticeKind(club);
        String oldKey = club.getMedicalNoticeObjectKey();
        club.setMedicalNoticeUrl(null);
        club.setMedicalNoticeObjectKey(null);
        club.setMedicalNoticeUpdatedAt(clock.instant());
        clubRepository.save(club);
        documentStore.deleteAfterCommit(oldKey);
        recordNotice(actor, club, "MEDICAL_NOTICE_REMOVED", kind);
    }

    /** Any signed-in user may read a club's notice: it is not secret, and it must be easy to reach. */
    @Transactional(readOnly = true)
    public ClubMedicalNoticeResponse getNotice(UUID clubId, User actor) {
        requireActor(actor);
        Club club = loadClub(clubId);
        if (club.getMedicalNoticeUrl() != null) {
            return new ClubMedicalNoticeResponse("URL", club.getMedicalNoticeUrl(), null);
        }
        if (club.getMedicalNoticeObjectKey() != null) {
            return new ClubMedicalNoticeResponse("FILE", documentStore.downloadUrl(club.getMedicalNoticeObjectKey()),
                    ClubDocumentStore.DOWNLOAD_URL_TTL.toSeconds());
        }
        throw noticeNotFound();
    }

    private void bumpNotice(Club club, Instant now) {
        club.setMedicalNoticeVersion(club.getMedicalNoticeVersion() + 1);
        club.setMedicalNoticeUpdatedAt(now);
    }

    private void recordNotice(User actor, Club club, String eventType, String kind) {
        auditEventService.record(
                eventType,
                actor.getId(),
                MedicalCertificateService.roleName(actor),
                "CLUB",
                club.getId().toString(),
                "kind=" + kind + ",version=" + club.getMedicalNoticeVersion());
    }

    static String noticeKind(Club club) {
        if (club.getMedicalNoticeUrl() != null) {
            return "URL";
        }
        return club.getMedicalNoticeObjectKey() != null ? "FILE" : null;
    }

    private static NotFoundException noticeNotFound() {
        return new NotFoundException("error.medical.notice_not_found", new Object[0], "The club has no privacy notice.");
    }

    // ---------------------------------------------------------------- conditions

    /** 409 {@code club_not_ready} with the list of what is missing, for turning collection on. */
    private void requireReady(Club club) {
        List<String> missing = compliance.missing(club);
        if (!missing.isEmpty()) {
            throw new ConflictWithPropertiesException(
                    "error.medical.club_not_ready",
                    new Object[0],
                    "The club cannot collect sport medical data yet. Missing: " + missing,
                    Map.of("missing", missing));
        }
    }

    private static String legalBasis(String raw) {
        String value = raw.trim();
        if (value.length() < LEGAL_BASIS_MIN || value.length() > LEGAL_BASIS_MAX) {
            throw new BadRequestException("error.medical.legal_basis", new Object[]{LEGAL_BASIS_MIN, LEGAL_BASIS_MAX},
                    "Name the law in 10 to 500 characters.");
        }
        return value;
    }

    private static String noticeUrl(String raw) {
        String value = raw.trim();
        if (!value.startsWith("https://") || value.length() <= "https://".length() || value.length() > NOTICE_URL_MAX
                || value.chars().anyMatch(Character::isWhitespace)) {
            throw new BadRequestException("error.medical.notice_url", new Object[0],
                    "The privacy notice link must start with https://.");
        }
        return value;
    }

    private String acceptedDpaVersion(String raw) {
        String current = compliance.currentDpaVersion();
        if (current == null) {
            throw new BadRequestException("error.medical.dpa_unavailable", new Object[0],
                    "The data processing agreement is not available yet.");
        }
        if (!current.equals(raw.trim())) {
            throw new BadRequestException("error.medical.dpa_version", new Object[]{current},
                    "Accept the current version of the data processing agreement: " + current);
        }
        return current;
    }

    // ---------------------------------------------------------------- switching off

    /**
     * Withdraws the current certificate that has a file of each profile that was required before this change and is
     * not required after it, and removes only the file of each profile that is still required but no longer needs one
     * (the row with its result, date and verification stays current). A dropped profile's date-only row is not touched
     * here: it stays for the 30-day grace that {@link MedicalRequirementService#recalculate} starts. A profile still
     * required, or still asked for a file, by another team or club is not counted and keeps what it has.
     * The "after" is read once the profiles are locked, so a switch of another club committed meanwhile counts.
     * The whole roster is locked here in one id-ordered query, the same set {@link MedicalRequirementService#recalculate}
     * locks next. Locking only the required subset first would let two switches each hold part of the other's set.
     * Without confirmation the whole change rolls back with one 409 that carries both counts: {@code affectedProfiles}
     * (certificates withdrawn) and {@code affectedFiles} (only the file removed).
     */
    private void removeFilesOfDroppedProfiles(List<UUID> profileIds, Set<UUID> requiredBefore, Set<UUID> fileBefore,
                                              Boolean confirmed, User actor) {
        if (requiredBefore.isEmpty()) {
            return;
        }
        medicalRequirement.lockProfiles(profileIds);
        Set<UUID> requiredAfter = medicalRequirement.requiredProfileIdsNow(requiredBefore);
        List<UUID> dropped = requiredBefore.stream().filter(id -> !requiredAfter.contains(id)).toList();
        Set<UUID> stillRequiredWithFile = fileBefore.stream().filter(requiredAfter::contains).collect(java.util.stream.Collectors.toSet());
        Set<UUID> fileAfter = stillRequiredWithFile.isEmpty() ? Set.of() : medicalRequirement.fileRequiredProfileIdsNow(stillRequiredWithFile);
        List<UUID> fileDropped = stillRequiredWithFile.stream().filter(id -> !fileAfter.contains(id)).toList();
        List<MedicalCertificate> affected = withFile(dropped);
        List<MedicalCertificate> affectedFiles = withFile(fileDropped);
        if (affected.isEmpty() && affectedFiles.isEmpty()) {
            return;
        }
        if (!Boolean.TRUE.equals(confirmed)) {
            Map<String, Object> counts = new HashMap<>();
            counts.put("affectedProfiles", affected.size());
            counts.put("affectedFiles", affectedFiles.size());
            if (!affected.isEmpty()) {
                throw new ConflictWithPropertiesException(
                        "error.medical.deletion_confirmation_required",
                        new Object[]{affected.size()},
                        affected.size() + " sport medical certificates would be deleted. Confirm to continue.",
                        counts);
            }
            throw new ConflictWithPropertiesException(
                    "error.medical.file_deletion_confirmation_required",
                    new Object[]{affectedFiles.size()},
                    affectedFiles.size() + " sport medical files would be deleted. Confirm to continue.",
                    counts);
        }
        Instant now = clock.instant();
        List<MedicalFileStore.KeyRef> keys = new ArrayList<>();
        for (MedicalCertificate row : affected) {
            keys.add(new MedicalFileStore.KeyRef(row.getObjectKey(), row.getId()));
            row.setWithdrawnAt(now);
            row.setObjectKey(null);
            certificateRepository.save(row);
            auditEventService.record(
                    "MEDICAL_CERTIFICATE_REMOVED_BY_POLICY",
                    actor.getId(),
                    MedicalCertificateService.roleName(actor),
                    "MEDICAL_CERTIFICATE",
                    row.getId().toString(),
                    "reason=switched_off");
        }
        for (MedicalCertificate row : affectedFiles) {
            keys.add(new MedicalFileStore.KeyRef(row.getObjectKey(), row.getId()));
            row.setObjectKey(null);
            certificateRepository.save(row);
            auditEventService.record(
                    "MEDICAL_FILE_REMOVED_BY_POLICY",
                    actor.getId(),
                    MedicalCertificateService.roleName(actor),
                    "MEDICAL_CERTIFICATE",
                    row.getId().toString(),
                    "reason=evidence_date_only");
        }
        fileStore.deleteAfterCommit(keys);
        log.info("Removed {} medical certificates and {} medical files after a settings change", affected.size(), affectedFiles.size());
    }

    private List<MedicalCertificate> withFile(List<UUID> profileIds) {
        if (profileIds.isEmpty()) {
            return List.of();
        }
        return certificateRepository.findCurrentByProfileIdIn(profileIds).stream()
                .filter(MedicalCertificate::hasFile)
                .toList();
    }

    private static MedicalEvidence parseEvidence(String raw, boolean allowInherit) {
        try {
            MedicalEvidence evidence = MedicalEvidence.valueOf(raw.trim().toUpperCase());
            if (evidence == MedicalEvidence.INHERIT && !allowInherit) {
                throw new IllegalArgumentException();
            }
            return evidence;
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("error.medical.invalid_evidence", new Object[0], "Unknown evidence mode: " + raw);
        }
    }

    private static MedicalMode parseMode(String raw, boolean allowInherit) {
        try {
            MedicalMode mode = MedicalMode.valueOf(raw.trim().toUpperCase());
            if (mode == MedicalMode.INHERIT && !allowInherit) {
                throw new IllegalArgumentException();
            }
            return mode;
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("error.medical.invalid_mode", new Object[0], "Unknown medical mode: " + raw);
        }
    }

    private Club loadClub(UUID clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("error.club.not_found", new Object[]{clubId}, "Club not found: " + clubId));
    }

    private ClubMedicalSettingsResponse toResponse(Club club) {
        String kind = noticeKind(club);
        ClubMedicalSettingsResponse.Notice notice = kind == null ? null
                : new ClubMedicalSettingsResponse.Notice(kind, club.getMedicalNoticeUpdatedAt(), club.getMedicalNoticeVersion());
        List<String> missing = compliance.missing(club);
        MedicalStorageGuard.Result storage = storageGuard.lastResult();
        return new ClubMedicalSettingsResponse(
                club.getId(),
                club.getMedicalMode().name(),
                club.getMedicalAcknowledgedAt(),
                club.getMedicalHistoryRetentionYears(),
                club.getMedicalEvidence().name(),
                club.getMedicalLegalBasis(),
                notice,
                club.getMedicalDpaVersion(),
                club.getMedicalDpaAcceptedAt(),
                compliance.currentDpaVersion(),
                compliance.dpaDocumentUrl(),
                compliance.noticeTemplateUrl(),
                missing.isEmpty(),
                missing,
                storage.secure(),
                storage.reasons(),
                club.isMedicalCoachFileAccess(),
                club.getMedicalCoachFileAccessAcknowledgedAt());
    }

    private static void requireActor(User actor) {
        if (actor == null || actor.getId() == null) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required.");
        }
    }
}
