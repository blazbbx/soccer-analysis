package com.example.footballanalysis.model.responses;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The club's sport medical settings and whether it may collect. {@code missing} lists what the club still lacks
 * (legalBasis, notice, dpa); {@code ready} is true when it is empty. {@code currentDpaVersion} and
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
        String dpaVersion,
        Instant dpaAcceptedAt,
        String currentDpaVersion,
        String dpaDocumentUrl,
        String noticeTemplateUrl,
        boolean ready,
        List<String> missing,
        boolean storageSecure,
        List<String> storageIssues,
        boolean coachFileAccess,
        Instant coachFileAccessAcknowledgedAt
) {
    /** {@code kind} is URL or FILE. Null as a whole while the club has no notice. */
    public record Notice(String kind, Instant updatedAt, int version) {}
}
