package com.example.footballanalysis.model.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The child's sport medical state. Fields a caller may not see stay null and are left out:
 * a coach gets only the date and the light, and a profile that does not need a certificate
 * returns {@code required: false} (plus the current file's id to the parent).
 * Only the player's own {@code /me/medical} carries {@code profileId}: the upload and file endpoints need it.
 * {@code adminAccesses} goes only to the parent: the platform admin's accesses that no club sees.
 * {@code notices} are the requiring clubs' privacy notices, shown next to the acknowledgement; not to the coach.
 * {@code resultAllowsTraining} and {@code resultAllowsCompeting} are what the paper says, not a permission: they come
 * only with {@code status}, and are shown only with its sentence. {@code fileRequired}: the next submission needs
 * the redacted copy; {@code fileRequestedNext}: it does, but the current row is date-only and stays valid until it ends.
 * {@code coachFileAccess}: a club asking for the file lets the coach open it now. {@code unseen} goes only to the
 * parent: someone else submitted the current row, or a break-glass access is new, and the parent has not looked yet.
 * {@code adult} and {@code parentAccessAllowed} come only on the player's own {@code /me/medical}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
public record MedicalStatusResponse(
        boolean required,
        String status,
        String result,
        Boolean resultAllowsTraining,
        Boolean resultAllowsCompeting,
        LocalDate validUntil,
        Integer daysRemaining,
        UUID certificateId,
        Boolean hasFile,
        Instant submittedAt,
        String submittedByFirstName,
        String submittedByLastName,
        String submittedByRole,
        List<MedicalHistoryItemResponse> history,
        Boolean canUploadMedical,
        UUID profileId,
        List<MedicalAdminAccessItem> adminAccesses,
        List<MedicalNoticeItem> notices,
        Boolean fileRequired,
        Boolean fileRequestedNext,
        Boolean verified,
        Instant verifiedAt,
        String verifiedByFirstName,
        String verifiedByLastName,
        String verifiedByRole,
        String verificationMethod,
        Boolean coachFileAccess,
        Boolean unseen,
        Boolean adult,
        Boolean parentAccessAllowed,
        String rejectionReason,
        Instant rejectedAt,
        String rejectedByRole,
        Instant dateCorrectedAt,
        String dateCorrectedByRole,
        LocalDate originalValidUntil
) {
    public static MedicalStatusResponse notRequired() {
        return builder().required(false).build();
    }

    public MedicalStatusResponse withProfileId(UUID id) {
        return toBuilder().profileId(id).build();
    }

    public MedicalStatusResponse withAdminAccesses(List<MedicalAdminAccessItem> items) {
        return toBuilder().adminAccesses(items).build();
    }
}
