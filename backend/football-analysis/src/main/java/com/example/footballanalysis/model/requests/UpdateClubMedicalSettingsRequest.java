package com.example.footballanalysis.model.requests;

/**
 * {@code mode} is ON or OFF. Turning it ON needs {@code acknowledged}: the club confirms that the law
 * requires the certificate and that the club answers for this choice, and the club must be ready: the operator
 * recorded its signed agreement, and it has a {@code legalBasis} and a notice ({@code noticeUrl} here or an uploaded
 * PDF). One request may set the last two and turn collection on.
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
        String evidence,
        Boolean coachFileAccess,
        Boolean coachFileAccessAcknowledged
) {}
