package com.example.footballanalysis.model.db.user;

import java.time.LocalDateTime;

import com.example.footballanalysis.model.db.Team;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)// leszármazottakkal egy táblában lesznek
@DiscriminatorColumn(name = "user_type", discriminatorType = DiscriminatorType.STRING) // megkülönböztető oszlop a típushoz
@Getter
@Setter
@NoArgsConstructor
public abstract class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String email;
    private String fullName;

    // 2. Nevezzük simán password-nek, vagy adjuk meg az oszlop nevét fixen
    @Column(name = "password", nullable = false)
    private String password;

    private LocalDateTime createdAt;

    // 3. Ezt a mezőt tartsuk meg a Spring Security és a logika miatt
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private UserRole role;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public abstract String getUserRole();

    public List<Team> getTeams() {
        // Ez egy absztrakt metódus, amit a Coach és Player osztályok implementálnak majd
        return null;
    }

}
