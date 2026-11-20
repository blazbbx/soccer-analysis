package com.example.footballanalysis.model.responses;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The club's sport medical settings and whether it may collect. {@code missing} lists what the club still lacks
 * (agreement, legalBasis, notice); {@code ready} is true when it is empty. {@code agreementVersion} is the version
 * of the club's live signed agreement that the platform admin recorded, null without one. {@code currentDpaVersion} and
 * {@code dpaDocumentUrl} are null while the approved agreement does not exist, {@code noticeTemplateUrl} while no
 * template does. {@code storageIssues} are the reason codes behind a false {@code storageSecure}.
 */
public record ClubMedicalSettingsResponse(
        UUID clubId,
        String mode,
        Instant acknowledgedAt,
        int historyRetentionYears,
        String evidence,
        String legalBasis,
        Notice notice,
        String currentDpaVersion,
        String dpaDocumentUrl,
        String noticeTemplateUrl,
        boolean ready,
        List<String> missing,
        boolean storageSecure,
        List<String> storageIssues,
        boolean coachFileAccess,
        Instant coachFileAccessAcknowledgedAt,
        String agreementVersion
) {
    /** {@code kind} is URL or FILE. Null as a whole while the club has no notice. */
    public record Notice(String kind, Instant updatedAt, int version) {}
}
