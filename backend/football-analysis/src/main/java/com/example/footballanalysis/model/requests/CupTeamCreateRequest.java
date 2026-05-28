package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CupTeamCreateRequest(
        @NotBlank(message = "{validation.cupteam.name.required}")
        @Size(min = 1, max = 100, message = "{validation.cupteam.name.size}")
        String name,

        UUID realTeamId
) {
}
