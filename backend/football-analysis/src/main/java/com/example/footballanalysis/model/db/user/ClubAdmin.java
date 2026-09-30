package com.example.footballanalysis.model.db.user;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("CLUB_ADMIN")
@Getter
@Setter
@NoArgsConstructor
public class ClubAdmin extends User {

    @Override
    public String getUserRole() {
        return "CLUB_ADMIN";
    }
}
