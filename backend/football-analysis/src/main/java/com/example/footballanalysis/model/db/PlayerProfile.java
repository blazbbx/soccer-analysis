package com.example.footballanalysis.model.db;

import com.example.footballanalysis.model.db.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "player_profiles")
@Getter
@Setter
@NoArgsConstructor
public class PlayerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_user_id", unique = true)
    private User linkedUser;

    @Column(name = "can_offer_rides", nullable = false)
    private boolean canOfferRides;

    @Column(name = "can_upload_medical", nullable = false)
    private boolean canUploadMedical;

    @Column(name = "medical_not_required_since")
    private Instant medicalNotRequiredSince;

    /** Since when the child no longer needs a file but still has one. The cleanup removes the file after 30 days. */
    @Column(name = "medical_file_not_required_since")
    private Instant medicalFileNotRequiredSince;

    @Column(name = "medical_retention_years")
    private Integer medicalRetentionYears;

    /** The clubs that last required the certificate, kept once it stops being required. Empty while it is required. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "medical_last_required_club_ids", nullable = false, columnDefinition = "uuid[]")
    private List<UUID> medicalLastRequiredClubIds = new ArrayList<>();

    /** An adult player lets the parents see the sport medical data. Has no meaning under 18. */
    @Column(name = "medical_parent_access_allowed", nullable = false)
    private boolean medicalParentAccessAllowed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
