package com.example.footballanalysis.model.requests;

import com.example.footballanalysis.model.Corner;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The user's confirmation after field detection: the four field corners, the goals as the user
 * left them on the preview, and the three jersey colors (home, away, referee) the team classifier
 * will compare against. The colors are pre-filled in the UI from the field-detection suggestion
 * and may have been edited.
 *
 * <p>{@code goals} may be absent or empty: the user deleted every detection, or the goal detection
 * was skipped. A pitch has at most two goals.
 */
public record ConfirmCornersRequest(
        @NotNull
        @Size(min = 4, max = 4, message = "Exactly four corners are required.")
        List<Corner> corners,

        @Valid
        @Size(max = 2, message = "A pitch has at most two goals.")
        List<ConfirmedGate> goals,

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
