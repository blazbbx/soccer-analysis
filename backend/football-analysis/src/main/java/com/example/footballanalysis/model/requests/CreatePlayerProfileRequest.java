package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreatePlayerProfileRequest(
        @NotBlank(message = "{validation.player_profile.firstName.required}")
        @Size(max = 255, message = "{validation.player_profile.firstName.max}")
        String firstName,

        @NotBlank(message = "{validation.player_profile.lastName.required}")
        @Size(max = 255, message = "{validation.player_profile.lastName.max}")
        String lastName,

        @PastOrPresent(message = "{validation.player_profile.dateOfBirth.past}")
        LocalDate dateOfBirth
) {}
