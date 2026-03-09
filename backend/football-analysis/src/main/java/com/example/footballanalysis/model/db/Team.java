package com.example.footballanalysis.model.db;

import java.util.*;

import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Player;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private List<Player> players = new ArrayList<>();

    @ManyToMany(mappedBy = "teams")
    private List<Coach> coaches = new ArrayList<>();


}
