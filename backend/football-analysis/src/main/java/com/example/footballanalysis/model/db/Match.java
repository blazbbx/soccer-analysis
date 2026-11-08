package com.example.footballanalysis.model.db;

import com.example.footballanalysis.model.db.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "matches")
@Getter
@Setter
@NoArgsConstructor
public class Match implements Persistable<UUID> {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_team_id")
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_team_id")
    private Team awayTeam;

    @Column(name = "away_team_name", length = 255)
    private String awayTeamName;

    // Ki töltötte fel – Keycloak bevezetéséig null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id")
    private User uploadedBy;

    // Meccs metaadatok
    private LocalDateTime matchDate;  // mikor játszották
    private Integer homeScore;        // végeredmény, manuálisan töltik ki
    private Integer awayScore;
    @Column(name = "home_team_color", length = 100)
    private String homeTeamColor;
    @Column(name = "away_team_color", length = 100)
    private String awayTeamColor;
    @Column(name = "referee_color", length = 100)
    private String refereeColor;

    // MinIO / videó adatok
    private String originalFileName;
    private String savedMinioFileName;
    private String hlsManifestUrl;    // encoder worker állítja be
    private String trackingDataUrl;   // ML worker állítja be

    // Field detection: defisheyed preview frame + corner polygon
    private String defishedImageUrl;

    @Column(name = "field_corners_json", columnDefinition = "TEXT")
    private String fieldCornersJson;

    // React only looks at this one (UPLOADING, PREPROCESSING, AWAITING_CORNERS, PROCESSING, READY, ERROR)
    private String overallStatus;

    // Spring Boot uses these to track the background workers (PENDING, COMPLETED, FAILED)
    private String mlStatus;
    private String encodingStatus;
    private String fieldDetectionStatus;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Override
    @Transient
    public boolean isNew() {
        return this.createdAt == null;
    }

    public boolean isFullyProcessed() {
        return "COMPLETED".equals(this.mlStatus) && "COMPLETED".equals(this.encodingStatus);
    }
    
}