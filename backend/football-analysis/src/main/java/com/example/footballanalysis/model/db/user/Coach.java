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
        @Fetch(FetchMode.SUBSELECT)
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

