package com.example.footballanalysis.model.db;

import com.example.footballanalysis.model.db.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(
        name = "ride_seats",
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_ride_seats_event_profile", columnNames = {"event_id", "player_profile_id"})
        },
        indexes = {
                @Index(name = "ix_ride_seats_offer_event", columnList = "offer_id, event_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class RideSeat {

    @Id
    private UUID id;

    /**
     * Composite FK to ride_offers (id, event_id). There is no single-column offer_id FK.
     * event_id is also a direct FK to team_events so deleting the event removes the seat.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(value = {
            @JoinColumn(name = "offer_id", referencedColumnName = "id", nullable = false),
            @JoinColumn(name = "event_id", referencedColumnName = "event_id", nullable = false)
    }, foreignKey = @ForeignKey(name = "fk_ride_seats_offer_event"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RideOffer offer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ride_seats_profile"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PlayerProfile playerProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booked_by_user_id", foreignKey = @ForeignKey(name = "fk_ride_seats_booked_by"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User bookedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        }
    }
}
