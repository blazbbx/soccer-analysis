package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateTeamRequest(
        @NotBlank(message = "{validation.team.name.required}")
        @Size(max = 100, message = "{validation.team.name.max}")
        String name,

        @Size(max = 20, message = "{validation.team.shortName.max}")
        String shortName,

        @Size(max = 500, message = "{validation.team.logoUrl.max}")
        String logoUrl,

        UUID clubId
) {
    public CreateTeamRequest(String name, String shortName, String logoUrl) {
        this(name, shortName, logoUrl, null);
    }
}
