package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCupRequest(
        @NotBlank(message = "{validation.cup.name.required}")
        @Size(min = 1, max = 100, message = "{validation.cup.name.size}")
        String name
) {
}
