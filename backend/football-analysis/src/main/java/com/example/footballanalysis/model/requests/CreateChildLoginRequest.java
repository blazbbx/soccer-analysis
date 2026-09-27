package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Szülő (vagy admin) a gyerek profiljához saját belépést hoz létre, meghívó link nélkül.
 * A kereszt- és vezetéknév üresen hagyható: ilyenkor a keretprofil neve kerül a fiókra.
 */
public record CreateChildLoginRequest(
        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "{validation.email.invalid}")
        @Size(max = 255, message = "{validation.email.max}")
        String email,

        @NotBlank(message = "{validation.password.required}")
        @Size(min = 6, max = 72, message = "{validation.password.size}")
        String password,

        @Size(min = 2, max = 100, message = "{validation.firstName.size}")
        String firstName,

        @Size(min = 2, max = 100, message = "{validation.lastName.size}")
        String lastName
) {}
