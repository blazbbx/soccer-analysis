package com.example.footballanalysis.model.db;

import com.example.footballanalysis.model.clip.ClipSyncEvent;
import com.example.footballanalysis.model.db.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "clips")
@Getter
@Setter
@NoArgsConstructor
public class Clip implements Persistable<UUID> {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coach_id")
    private User createdBy;       // melyik felhasználó hozta létre a klippet

    private String name;

    @Transient
    private List<ClipSyncEvent> syncData;

    @Column(name = "render_status", length = 50)
    private String renderStatus;

    @Column(name = "render_error", length = 1000)
    private String renderError;

    @Transient
    private LocalDateTime renderRequestedAt;

    @Column(name = "rendered_at")
    private LocalDateTime renderedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
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
}
