package com.example.footballanalysis.model.requests;

import java.time.LocalDate;

public record UpdateUserRequest(
    String firstName,
    String lastName,
    LocalDate dateOfBirth
) {
    public UpdateUserRequest(String firstName, String lastName) {
        this(firstName, lastName, null);
    }
}
