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
 * A sport medical file still to be deleted from the bucket. Written in the transaction that gives up the key, removed
 * only after the object is gone, so a failed delete is retried by the nightly cleanup instead of leaving an orphan.
 */
@Entity
@Table(name = "medical_file_deletions")
@Getter
@Setter
@NoArgsConstructor
public class MedicalFileDeletion {

    @Id
    private UUID id;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "certificate_id")
    private UUID certificateId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
