package com.example.footballanalysis.model.db;

import com.example.footballanalysis.model.db.user.Coach;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "clips")
@Getter
@Setter
@NoArgsConstructor
public class Clip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coach_id")
    private Coach createdBy;       // melyik edző hozta létre a klippet

    private Integer startSeconds;   // klipp kezdete a meccs videóban
    private Integer endSeconds;     // klipp vége a meccs videóban

    private String name;

    private String minioUrl;        // clips/{matchId}/{clipId}.mp4

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
