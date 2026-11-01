package com.example.footballanalysis.model.responses;

import java.util.UUID;

/**
 * A requiring club's privacy notice as the submitter sees it next to the acknowledgement. {@code noticeKind} is
 * URL or FILE, or null while the club has none; the link itself comes from {@code GET /api/clubs/{clubId}/medical-notice}.
 */
public record MedicalNoticeItem(UUID clubId, String clubName, String legalBasis, String noticeKind) {}
