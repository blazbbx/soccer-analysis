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
import jakarta.persistence.UniqueConstraint;
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
        name = "homework_assignments",
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_homework_assignments_homework_profile", columnNames = {"homework_id", "player_profile_id"})
        },
        indexes = {
                @Index(name = "ix_homework_assignments_profile_status", columnList = "player_profile_id, status"),
                @Index(name = "ix_homework_assignments_homework_status", columnList = "homework_id, status"),
                @Index(name = "ix_homework_assignments_submitted_by", columnList = "submitted_by_user_id"),
                @Index(name = "ix_homework_assignments_reviewed_by", columnList = "reviewed_by_user_id"),
                @Index(name = "ix_homework_assignments_returned_by", columnList = "returned_by_user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class HomeworkAssignment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "homework_id", nullable = false, foreignKey = @ForeignKey(name = "fk_homework_assignments_homework"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Homework homework;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_homework_assignments_profile"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PlayerProfile playerProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HomeworkAssignmentStatus status;

    @Column(name = "response_text", columnDefinition = "TEXT")
    private String responseText;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    /** The recipient's first opening. There is no separate status for it: until the hand-in the status stays ASSIGNED. */
    @Column(name = "first_viewed_at")
    private LocalDateTime firstViewedAt;

    /** Set once, at the hand-in. Until then the assignment can be taken back and its questions can change. */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_user_id", foreignKey = @ForeignKey(name = "fk_homework_assignments_submitted_by"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User submittedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id", foreignKey = @ForeignKey(name = "fk_homework_assignments_reviewed_by"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User reviewedBy;

    @Column(name = "coach_feedback", columnDefinition = "TEXT")
    private String coachFeedback;

    /** The last time the coach sent the work back for a fix. Kept after the next hand-in, for the deadline flags. */
    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "returned_by_user_id", foreignKey = @ForeignKey(name = "fk_homework_assignments_returned_by"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User returnedBy;

    @Column(name = "return_note", columnDefinition = "TEXT")
    private String returnNote;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        }
    }
}
