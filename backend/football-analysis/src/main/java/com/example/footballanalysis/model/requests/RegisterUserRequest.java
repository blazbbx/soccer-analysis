package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Meghívó linkkel indított regisztrációhoz szükséges adatok.
 */
public record RegisterUserRequest(
        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "{validation.email.invalid}")
        @Size(max = 255, message = "{validation.email.max}")
        String email,

        @NotBlank(message = "{validation.firstName.required}")
        @Size(min = 2, max = 100, message = "{validation.firstName.size}")
        String firstName,

        @NotBlank(message = "{validation.lastName.required}")
        @Size(min = 2, max = 100, message = "{validation.lastName.size}")
        String lastName,

        @NotBlank(message = "{validation.password.required}")
        @Size(min = 6, max = 72, message = "{validation.password.size}")
        String password,

        @NotBlank(message = "Invite token is required.")
        String inviteToken
) {}