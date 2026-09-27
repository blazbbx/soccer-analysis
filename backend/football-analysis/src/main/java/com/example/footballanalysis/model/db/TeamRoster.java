package com.example.footballanalysis.model.db;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "team_roster")
@Getter
@Setter
@NoArgsConstructor
public class TeamRoster {

    @EmbeddedId
    private TeamRosterId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("playerProfileId")
    @JoinColumn(name = "player_profile_id", nullable = false)
    private PlayerProfile playerProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("teamId")
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;
}
