package com.example.footballanalysis.model.responses;

import java.time.Instant;
import java.util.UUID;

/**
 * One signed data processing agreement of a club. {@code active} while it is live; otherwise {@code endKind} is
 * WITHDRAWN (with the platform admin's {@code withdrawalReason}) or REPLACED (a newer one took its place).
 */
public record ClubMedicalAgreementResponse(
        UUID id,
        String dpaVersion,
        Instant recordedAt,
        boolean active,
        Instant endedAt,
        String endKind,
        String withdrawalReason
) {}
