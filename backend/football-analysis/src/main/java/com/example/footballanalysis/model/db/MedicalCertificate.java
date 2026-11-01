package com.example.footballanalysis.model.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One submitted sport medical certificate. A FILE row is pending until the uploaded file is checked
 * ({@code completedAt}), then current until it is superseded or withdrawn; a DATE_ONLY row has no file and is
 * current at once. Old rows keep the result and the date as history; their file is deleted.
 * {@code result} is empty only on rows from before the result existed ({@code sport-medical-v1}).
 */
@Entity
@Table(name = "medical_certificates")
@Getter
@Setter
@NoArgsConstructor
public class MedicalCertificate {

    /** The acknowledgement before the result and the evidence mode existed. Only old rows carry it. */
    public static final String ACKNOWLEDGEMENT_V1 = "sport-medical-v1";
    public static final String ACKNOWLEDGEMENT_DATE = "sport-medical-v2-date";
    public static final String ACKNOWLEDGEMENT_FILE = "sport-medical-v2-file";
    public static final String ACKNOWLEDGEMENT_IN_PERSON = "sport-medical-v2-in-person";

    /** Whether the row came with a file. A FILE row keeps FILE after its file was removed: {@link #hasFile()} looks at the key. */
    public enum Evidence {
        DATE_ONLY,
        FILE
    }

    /** What the verifier compared the result and the date with: the original paper in person, or the uploaded file. */
    public enum VerificationMethod {
        ORIGINAL,
        FILE
    }

    @Id
    private UUID id;

    @Column(name = "player_profile_id", nullable = false)
    private UUID playerProfileId;

    /** End of validity or of the suspension; empty for NEM_ALKALMAS and VEGLEGESEN_ELTILTVA. */
    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 24)
    private MedicalResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence", nullable = false, length = 16)
    private Evidence evidence = Evidence.FILE;

    @Column(name = "object_key", length = 512)
    private String objectKey;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "submitted_by_user_id")
    private UUID submittedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "submitted_by_role", nullable = false, length = 20)
    private MedicalSubmitterRole submittedByRole;

    @Column(name = "acknowledged_at", nullable = false)
    private Instant acknowledgedAt;

    @Column(name = "acknowledgement_version", nullable = false, length = 40)
    private String acknowledgementVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    /** Set together with the verifier and the method; empty until a club admin or a coach verified the row. */
    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by_user_id")
    private UUID verifiedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verified_by_role", length = 20)
    private MedicalSubmitterRole verifiedByRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_method", length = 16)
    private VerificationMethod verificationMethod;

    /** Set together with the role and the reason. A rejected row is also withdrawn, and its file is deleted. */
    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejected_by_user_id")
    private UUID rejectedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejected_by_role", length = 20)
    private MedicalSubmitterRole rejectedByRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 24)
    private MedicalRejectionReason rejectionReason;

    /** Set when a verifier corrected {@code validUntil} from the paper; {@code originalValidUntil} is what was entered first. */
    @Column(name = "date_corrected_at")
    private Instant dateCorrectedAt;

    @Column(name = "date_corrected_by_user_id")
    private UUID dateCorrectedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "date_corrected_by_role", length = 20)
    private MedicalSubmitterRole dateCorrectedByRole;

    @Column(name = "original_valid_until")
    private LocalDate originalValidUntil;

    public boolean isPending() {
        return completedAt == null && supersededAt == null && withdrawnAt == null;
    }

    public boolean isCurrent() {
        return completedAt != null && supersededAt == null && withdrawnAt == null;
    }

    public boolean hasFile() {
        return objectKey != null;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }
}
