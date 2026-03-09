package com.example.footballanalysis.model.db.user;

import com.example.footballanalysis.model.db.Team;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@DiscriminatorValue("PLAYER")
@Getter
@Setter
@NoArgsConstructor
public class Player extends User {

    @ManyToMany
    @JoinTable(
            name = "player_teams", // Ez a kapcsolótábla neve
            joinColumns = @JoinColumn(name = "player_id"),
            inverseJoinColumns = @JoinColumn(name = "team_id")
    )
    private List<Team> teams = new ArrayList<>();

    public void addTeam(Team team) {
        this.teams.add(team);
        team.getPlayers().add(this);
    }

    public void removeTeam(Team team) {
        if (this.teams.contains(team)) {
            this.teams.remove(team);
            team.getPlayers().remove(this);
        }
    }

    @Override
    public String getUserRole() {
        return "PLAYER";
    }
}
