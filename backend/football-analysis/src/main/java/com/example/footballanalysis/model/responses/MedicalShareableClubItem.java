package com.example.footballanalysis.model.responses;

import java.util.UUID;

/**
 * A requiring and ready club that does not see the current certificate yet, with what the share dialog shows: the law
 * and the kind of its privacy notice (the link comes from {@code GET /api/clubs/{clubId}/medical-notice}).
 * {@code needsFile}: the club asks for the redacted copy and this certificate has none, so it cannot be shared; a new
 * submission with the file is needed.
 */
public record MedicalShareableClubItem(UUID clubId, String clubName, String legalBasis, String noticeKind, boolean needsFile) {}
