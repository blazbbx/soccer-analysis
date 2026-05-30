package com.example.footballanalysis.model.db.user;

import java.util.HashSet;
import java.util.Set;

import com.example.footballanalysis.model.db.Team;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

@Entity
@DiscriminatorValue("FAN")
@Getter
@Setter
@NoArgsConstructor
public class Fan extends User {

        @ManyToMany(fetch = FetchType.EAGER)
        @JoinTable(name = "fan_favorite_teams",
            joinColumns = @JoinColumn(name = "fan_id"),
            inverseJoinColumns = @JoinColumn(name = "team_id"))
        @JsonIgnoreProperties({"coaches", "players"})
        @Fetch(FetchMode.SUBSELECT)
        private Set<Team> teams = new HashSet<>();

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

