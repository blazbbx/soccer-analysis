package com.example.footballanalysis.model.db;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "matches") // 'match' is a reserved SQL keyword in some databases, so we use 'matches'
@Getter
@Setter
public class Match implements Persistable<UUID> {

    @Id
    private UUID id;

    // TODO lehet hogy nem ID hanem csak name kéne legyen, ki kell találni
    // We will map these to actual Team entities later,
    // but for now, we just store the UUIDs to keep it simple.
    private UUID homeTeamId;
    private UUID awayTeamId;

    // Our MinIO data
    private String originalFileName;
    private String savedMinioFileName; // The UUID + filename we generate

    private String hlsManifestUrl;  // Set by Encoder Worker
    private String trackingDataUrl;

    // React only looks at this one (UPLOADING, PROCESSING, READY, ERROR)
    private String overallStatus;

    // Spring Boot uses these to track the background workers (PENDING, COMPLETED, FAILED)
    private String mlStatus;
    private String encodingStatus;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Override
    @Transient // Tells Hibernate not to create a column for this in the DB
    public boolean isNew() {
        return this.createdAt == null;
    }
    // Helper method to check if everything is fully done
    public boolean isFullyProcessed() {
        return "COMPLETED".equals(this.mlStatus) && "COMPLETED".equals(this.encodingStatus);
    }
}