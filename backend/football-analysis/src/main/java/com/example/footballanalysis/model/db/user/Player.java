package com.example.footballanalysis.model.db.user;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("PLAYER")
@Getter
@Setter
@NoArgsConstructor
public class Player extends User {

    @Override
    public String getUserRole() {
        return "PLAYER";
    }
}
