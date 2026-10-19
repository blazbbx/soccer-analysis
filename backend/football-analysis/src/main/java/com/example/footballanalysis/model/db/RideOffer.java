package com.example.footballanalysis.model.db;

import com.example.footballanalysis.model.db.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
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
        name = "ride_offers",
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_ride_offers_id_event", columnNames = {"id", "event_id"}),
                @UniqueConstraint(name = "ux_ride_offers_event_driver", columnNames = {"event_id", "driver_user_id"})
        },
        indexes = {
                @Index(name = "ix_ride_offers_event_id", columnList = "event_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class RideOffer {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ride_offers_event"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TeamEvent event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ride_offers_driver"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User driver;

    @Column(name = "seats_total", nullable = false)
    private int seatsTotal;

    @Column(name = "departure_place", nullable = false, length = 255)
    private String departurePlace;

    @Column(name = "departure_at", nullable = false)
    private LocalDateTime departureAt;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }
}
