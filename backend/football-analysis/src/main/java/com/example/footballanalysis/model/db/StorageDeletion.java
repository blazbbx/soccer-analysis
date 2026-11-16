package com.example.footballanalysis.model.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A MinIO object (or every object under a prefix) still to be deleted. Written in the transaction that deletes the
 * match, team or clip, removed only after the objects are gone: see {@code MinioObjectCleanupService}.
 */
@Entity
@Table(name = "storage_deletions")
@Getter
@Setter
@NoArgsConstructor
public class StorageDeletion {

    @Id
    private UUID id;

    @Column(name = "bucket", nullable = false, length = 100)
    private String bucket;

    @Column(name = "object_key", nullable = false, length = 1024)
    private String objectKey;

    @Column(name = "is_prefix", nullable = false)
    private boolean prefix;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
