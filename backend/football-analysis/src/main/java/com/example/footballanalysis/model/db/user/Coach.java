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
@DiscriminatorValue("COACH")
@Getter
@Setter
@NoArgsConstructor
public class Coach extends User {

    @ManyToMany
    @JoinTable(
            name = "coach_teams",
            joinColumns = @JoinColumn(name = "coach_id"),
            inverseJoinColumns = @JoinColumn(name = "team_id")
    )
    private Set<Team> teams = new HashSet<>();

    public void addTeam(Team team) {
        boolean added = this.teams.add(team);
        if (added) {
            team.getCoaches().add(this);
        }
    }

    public void removeTeam(Team team) {
        if (this.teams.contains(team)) {
            this.teams.remove(team);
            team.getCoaches().remove(this);
        }
    }

    @Override
    public String getUserRole() {
        return "COACH";
    }
}

