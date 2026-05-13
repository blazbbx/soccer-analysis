package com.example.footballanalysis.model.db;

import com.example.footballanalysis.model.db.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "cups",
        indexes = {
                @Index(name = "idx_cup_created_by", columnList = "created_by_id"),
                @Index(name = "idx_cup_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Cup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "cup", orphanRemoval = true, cascade = CascadeType.ALL)
    @Fetch(FetchMode.SUBSELECT)
    private Set<CupTeam> teams = new HashSet<>();

    @OneToMany(mappedBy = "cup", orphanRemoval = true, cascade = CascadeType.ALL)
    @Fetch(FetchMode.SUBSELECT)
    private Set<CupMatch> matches = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
