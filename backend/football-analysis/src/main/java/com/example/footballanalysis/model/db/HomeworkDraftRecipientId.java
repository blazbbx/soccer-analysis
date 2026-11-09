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
public class HomeworkDraftRecipientId implements Serializable {

    @Column(name = "homework_id")
    private UUID homeworkId;

    @Column(name = "player_profile_id")
    private UUID playerProfileId;
}
