package com.example.footballanalysis.model.db.user;

import com.example.footballanalysis.model.db.Team;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.HashSet;
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
            inverseJoinColumns = @JoinColumn(name = "team_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "team_id"})
    )
        @Fetch(FetchMode.SUBSELECT)
    private Set<Team> teams = new HashSet<>();

    public void addTeam(Team team) {
        boolean added = this.teams.add(team);
        if (added) {
            team.getPlayers().add(this);
        }
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
