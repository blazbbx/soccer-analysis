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
import java.util.UUID;

/**
 * A club's signed data processing agreement: the platform admin uploads the signed PDF, nothing else. While one is live
 * for the current agreement version, sport medical collection is available to the club (it still turns it on itself).
 * The club id is not a JPA relation and the club's name is copied: the row is the operator's proof and outlives the club.
 */
@Entity
@Table(name = "club_medical_agreements")
@Getter
@Setter
@NoArgsConstructor
public class ClubMedicalAgreement {

    public enum EndKind {
        /** The platform admin withdrew it, with a reason. */
        WITHDRAWN,
        /** A newer signed agreement took its place. */
        REPLACED
    }

    @Id
    private UUID id;

    @Column(name = "club_id")
    private UUID clubId;

    @Column(name = "club_name", nullable = false)
    private String clubName;

    @Column(name = "dpa_version", nullable = false, length = 20)
    private String dpaVersion;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "recorded_by_user_id")
    private UUID recordedByUserId;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "ended_by_user_id")
    private UUID endedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_kind", length = 16)
    private EndKind endKind;

    @Column(name = "withdrawal_reason", length = 500)
    private String withdrawalReason;

    public boolean isActive() {
        return endedAt == null;
    }
}
