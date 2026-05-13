package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateMatchScoreRequest(
        @NotNull(message = "{validation.cupmatch.home_score.required}")
        @Min(value = 0, message = "{validation.cupmatch.score.min}")
        Integer homeScore,

        @NotNull(message = "{validation.cupmatch.away_score.required}")
        @Min(value = 0, message = "{validation.cupmatch.score.min}")
        Integer awayScore
) {
}
