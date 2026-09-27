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
public class PlayerParentId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "player_profile_id")
    private UUID playerProfileId;
}
