package com.example.footballanalysis.model.requests;

import com.example.footballanalysis.model.Corner;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The user's confirmation after field detection: the four field corners and the three jersey
 * colors (home, away, referee) the team classifier will compare against. The colors are
 * pre-filled in the UI from the field-detection suggestion and may have been edited.
 */
public record ConfirmCornersRequest(
        @NotNull
        @Size(min = 4, max = 4, message = "Exactly four corners are required.")
        List<Corner> corners,

        @NotBlank(message = "{validation.match.color.required}")
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "{validation.match.color.format}")
        String homeTeamColor,

        @NotBlank(message = "{validation.match.color.required}")
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "{validation.match.color.format}")
        String awayTeamColor,

        @NotBlank(message = "{validation.match.color.required}")
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "{validation.match.color.format}")
        String refereeColor
) {}
