package com.example.footballanalysis.model.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One break-glass access of a platform admin to a child's sport medical data, with the reason.
 * The profile id is not a foreign key and the child's name is copied, so the club can still read the row
 * after the profile is deleted. {@code clubIds} are the clubs the row is shown to.
 */
@Entity
@Table(name = "medical_admin_accesses")
@Getter
@Setter
@NoArgsConstructor
public class MedicalAdminAccess {

    public enum Action {
        READ,
        WITHDRAWN
    }

    @Id
    private UUID id;

    @Column(name = "certificate_id")
    private UUID certificateId;

    @Column(name = "player_profile_id", nullable = false)
    private UUID playerProfileId;

    @Column(name = "player_name", nullable = false)
    private String playerName;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "club_ids", nullable = false, columnDefinition = "uuid[]")
    private List<UUID> clubIds = new ArrayList<>();

    @Column(name = "admin_user_id")
    private UUID adminUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private Action action;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
