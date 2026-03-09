package com.example.footballanalysis.model.db.user;

import java.util.ArrayList;
import java.util.List;

import com.example.footballanalysis.model.db.Team;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("FAN")
@Getter
@Setter
@NoArgsConstructor
public class Fan extends User {

        @ManyToMany
        @JoinTable(name = "fan_favorite_teams",
            joinColumns = @JoinColumn(name = "fan_id"),
            inverseJoinColumns = @JoinColumn(name = "team_id"))
        @JsonIgnoreProperties({"coaches", "players"})
        private List<Team> teams = new ArrayList<>();

        public void addTeam(Team team) {
            this.teams.add(team);
        }

        public void removeTeam(Team team) {
            this.teams.remove(team);
        }

    @Override
    public String getUserRole() {
        return "FAN";
    }
}

