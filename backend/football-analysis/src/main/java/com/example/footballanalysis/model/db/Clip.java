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

    private Integer startSeconds;   // klipp kezdete a meccs videóban
    private Integer endSeconds;     // klipp vége a meccs videóban

    private String name;

    @Column(name = "bucket_name")
    private String bucket;

    @Column(name = "object_key")
    private String objectKey;

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

    public void setStorageLocation(String bucket, String objectKey) {
        this.bucket = bucket;
        this.objectKey = objectKey;
    }
}
