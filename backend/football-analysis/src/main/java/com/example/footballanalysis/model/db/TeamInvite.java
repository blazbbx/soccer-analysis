package com.example.footballanalysis.model.db;

import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "team_invitations",
        indexes = {
                @Index(name = "idx_team_invitations_token", columnList = "token", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
public class TeamInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_coach_id")
    private Coach createdByCoach;

    @Enumerated(EnumType.STRING)
    @Column(name = "invited_role", nullable = false, length = 20)
    private UserRole invitedRole;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(nullable = false)
    private int maxUses;

    @Column(nullable = false)
    private int usedCount;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isExhausted() {
        return usedCount >= maxUses;
    }
}