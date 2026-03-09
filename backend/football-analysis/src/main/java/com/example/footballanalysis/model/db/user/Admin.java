package com.example.footballanalysis.model.db.user;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;

@Entity
@DiscriminatorValue("ADMIN")
@NoArgsConstructor
public class Admin extends User {

    @Override
    public String getUserRole() {
        return "ADMIN";
    }
}

