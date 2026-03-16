package com.example.footballanalysis.model.db.user;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    @Column(name = "first_name")
    private String firstName;
    @Column(name = "last_name")
    private String lastName;

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

    public String getFullName() {
        return ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
    }

    public abstract String getUserRole();

}
