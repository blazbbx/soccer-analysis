package com.example.footballanalysis.model.db;

import java.util.*;

import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Player;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

@Entity
@Table(name = "teams")
@Getter
@Setter
@NoArgsConstructor
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;       
    private String shortName; 
    private String logoUrl;

    @ManyToMany(mappedBy = "teams")
    @Fetch(FetchMode.SUBSELECT)
    private Set<Player> players = new HashSet<>();

    @ManyToMany(mappedBy = "teams")
    @Fetch(FetchMode.SUBSELECT)
    private Set<Coach> coaches = new HashSet<>();

    public Set<Player> getPlayers() {
        return players;
    }

    public Set<Coach> getCoaches() {
        return coaches;
    }

}
