package com.example.footballanalysis.model.requests;

/**
 * {@code mode} is ON or OFF. Turning it ON needs {@code acknowledged}: the club confirms that the law
 * requires the certificate and that the club answers for this choice, and the club must be ready: a
 * {@code legalBasis}, a notice ({@code noticeUrl} here or an uploaded PDF) and the accepted agreement
 * ({@code acceptDpaVersion}, which must equal the current version). One request may set all of them.
 * {@code evidence} is DATE_ONLY or REDACTED_FILE. Turning {@code coachFileAccess} on needs
 * {@code coachFileAccessAcknowledged} and a file mode in the club. A null field leaves the stored value as it is.
 */
public record UpdateClubMedicalSettingsRequest(
        String mode,
        Boolean acknowledged,
        Boolean confirmDeletion,
        Integer historyRetentionYears,
        String legalBasis,
        String noticeUrl,
        String acceptDpaVersion,
        String evidence,
        Boolean coachFileAccess,
        Boolean coachFileAccessAcknowledged
) {}
