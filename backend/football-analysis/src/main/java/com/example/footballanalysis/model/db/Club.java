package com.example.footballanalysis.model.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "clubs")
@Getter
@Setter
@NoArgsConstructor
public class Club {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String shortName;

    private String logoUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "medical_mode", nullable = false, length = 8)
    private MedicalMode medicalMode = MedicalMode.OFF;

    @Column(name = "medical_acknowledged_at")
    private Instant medicalAcknowledgedAt;

    @Column(name = "medical_acknowledged_by_user_id")
    private UUID medicalAcknowledgedByUserId;

    @Column(name = "medical_history_retention_years", nullable = false)
    private int medicalHistoryRetentionYears = 3;

    /** DATE_ONLY or REDACTED_FILE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "medical_evidence", nullable = false, length = 16)
    private MedicalEvidence medicalEvidence = MedicalEvidence.DATE_ONLY;

    /** The law that obliges the club to know the certificate, in the club's own words. */
    @Column(name = "medical_legal_basis", length = 500)
    private String medicalLegalBasis;

    /** The club's privacy notice: an https link or an uploaded PDF in the club-documents bucket, never both. */
    @Column(name = "medical_notice_url", length = 1000)
    private String medicalNoticeUrl;

    @Column(name = "medical_notice_object_key", length = 512)
    private String medicalNoticeObjectKey;

    /** Raised on every change of the notice; the submission audit records it. */
    @Column(name = "medical_notice_version", nullable = false)
    private int medicalNoticeVersion;

    @Column(name = "medical_notice_updated_at")
    private Instant medicalNoticeUpdatedAt;

    /** The version of the data processing agreement the club accepted. */
    @Column(name = "medical_dpa_version", length = 20)
    private String medicalDpaVersion;

    @Column(name = "medical_dpa_accepted_at")
    private Instant medicalDpaAcceptedAt;

    @Column(name = "medical_dpa_accepted_by_user_id")
    private UUID medicalDpaAcceptedByUserId;

    /** The coaches of the club's file-asking ON teams may open the redacted copy. No effect without a file mode. */
    @Column(name = "medical_coach_file_access", nullable = false)
    private boolean medicalCoachFileAccess;

    @Column(name = "medical_coach_file_access_acknowledged_at")
    private Instant medicalCoachFileAccessAcknowledgedAt;

    @Column(name = "medical_coach_file_access_acknowledged_by_user_id")
    private UUID medicalCoachFileAccessAcknowledgedByUserId;

    @OneToMany(mappedBy = "club")
    private List<Team> teams = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
