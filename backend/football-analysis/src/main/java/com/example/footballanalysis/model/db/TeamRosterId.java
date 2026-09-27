package com.example.footballanalysis.model.db;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TeamRosterId implements Serializable {

    @Column(name = "player_profile_id")
    private UUID playerProfileId;

    @Column(name = "team_id")
    private UUID teamId;
}
