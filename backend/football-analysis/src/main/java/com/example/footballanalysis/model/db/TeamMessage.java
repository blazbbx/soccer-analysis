package com.example.footballanalysis.model.db;

import com.example.footballanalysis.model.db.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "team_messages", indexes = {
        @Index(name = "idx_team_messages_team_id", columnList = "team_id"),
        @Index(name = "idx_team_messages_created_at", columnList = "created_at"),
        @Index(name = "ix_team_messages_team_created_id", columnList = "team_id, created_at DESC, id DESC")
})
@Getter
@Setter
@NoArgsConstructor
public class TeamMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 16, nullable = false)
    private TeamMessageType type = TeamMessageType.TEXT;

    /** The shared clip of a CLIP message; null for TEXT, and after the clip was deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clip_id")
    private Clip clip;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        // Postgres keeps microseconds; truncating here makes the returned value equal to the stored one,
        // so it works as an exact cursor.
        this.createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
