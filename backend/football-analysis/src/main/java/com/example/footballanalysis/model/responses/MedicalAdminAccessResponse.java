package com.example.footballanalysis.model.responses;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * The answer to a platform admin's access: technical facts for troubleshooting and for a withdrawal. No result, no
 * validity, no history content, no submitter and no file. The page does not keep it: the next look needs a new reason.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MedicalAdminAccessResponse(
        boolean required,
        boolean fileRequired,
        UUID certificateId,
        boolean hasFile,
        String evidence,
        String contentType,
        Long sizeBytes,
        Instant submittedAt,
        boolean verified,
        int historyCount,
        int pendingCount,
        Instant notRequiredSince,
        Instant fileNotRequiredSince
) {
}
