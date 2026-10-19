package com.example.footballanalysis.model.db;

import com.example.footballanalysis.model.db.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_attendances")
@Getter
@Setter
@NoArgsConstructor
public class Attendance {

    @EmbeddedId
    private AttendanceId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("eventId")
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_event_attendances_event"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TeamEvent event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("playerProfileId")
    @JoinColumn(name = "player_profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_event_attendances_profile"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PlayerProfile playerProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(name = "wants_ride", nullable = false)
    private boolean wantsRide;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responded_by_user_id", foreignKey = @ForeignKey(name = "fk_event_attendances_responded_by"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User respondedBy;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "comment", length = 500)
    private String comment;
}
