package com.example.footballanalysis.model.db;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "cup_matches",
        indexes = {
                @Index(name = "idx_cupmatch_cup_id", columnList = "cup_id"),
                @Index(name = "idx_cupmatch_played", columnList = "played"),
                @Index(name = "idx_cupmatch_home_away", columnList = "home_team_id,away_team_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class CupMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cup_id", nullable = false)
    private Cup cup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_team_id", nullable = false)
    private CupTeam homeTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_team_id", nullable = false)
    private CupTeam awayTeam;

    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;

    @Column(nullable = false)
    private boolean played = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "real_match_id")
    private Match realMatch;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
