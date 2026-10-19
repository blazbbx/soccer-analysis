package com.example.footballanalysis.model.db;

import com.example.footballanalysis.model.db.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(
        name = "team_events",
        indexes = {
                @Index(name = "ix_team_events_team_starts", columnList = "team_id, starts_at, id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class TeamEvent {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false, foreignKey = @ForeignKey(name = "fk_team_events_team"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TeamEventType type;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "meeting_at", nullable = false)
    private LocalDateTime meetingAt;

    @Column(name = "venue", length = 255)
    private String venue;

    @Column(name = "meeting_point", nullable = false, length = 255)
    private String meetingPoint;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "rsvp_deadline")
    private LocalDateTime rsvpDeadline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_match_id", foreignKey = @ForeignKey(name = "fk_team_events_video_match"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Match videoMatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cup_match_id", foreignKey = @ForeignKey(name = "fk_team_events_cup_match"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private CupMatch cupMatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", foreignKey = @ForeignKey(name = "fk_team_events_created_by"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }
}
