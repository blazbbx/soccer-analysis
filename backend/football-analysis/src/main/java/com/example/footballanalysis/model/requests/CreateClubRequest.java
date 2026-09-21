package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateClubRequest(
        @NotBlank(message = "{validation.club.name.required}")
        @Size(max = 100, message = "{validation.club.name.max}")
        String name,

        @Size(max = 20, message = "{validation.club.shortName.max}")
        String shortName,

        @Size(max = 500, message = "{validation.club.logoUrl.max}")
        String logoUrl
) {
}
