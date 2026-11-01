package com.example.footballanalysis.model.db;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The current certificate a parent last looked at, one row per parent and child. A newer current row someone else
 * submitted, or a newer break-glass access shown to the parent, makes the child's card unread again.
 */
@Entity
@Table(name = "medical_parent_seen")
@Getter
@Setter
@NoArgsConstructor
public class MedicalParentSeen {

    @EmbeddedId
    private MedicalParentSeenId id;

    @Column(name = "certificate_id", nullable = false)
    private UUID certificateId;

    @Column(name = "seen_at", nullable = false)
    private Instant seenAt;
}
