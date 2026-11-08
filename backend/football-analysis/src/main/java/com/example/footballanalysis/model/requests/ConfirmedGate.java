package com.example.footballanalysis.model.requests;

import com.example.footballanalysis.model.Corner;
import com.example.footballanalysis.model.Goal;
import com.example.footballanalysis.model.GoalCorner;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A goal the user confirmed (possibly after moving corners, placing a missing one, or drawing it
 * by hand): four placed corners in the goal model's order P1 top-left, P2 top-right,
 * P3 bottom-left, P4 bottom-right.
 */
public record ConfirmedGate(
        @NotNull
        @Size(min = 4, max = 4, message = "Exactly four goal corners are required.")
        List<@NotNull Corner> corners
) {

    public Goal toGoal() {
        return new Goal(corners.stream().map(c -> GoalCorner.confirmed(c.x(), c.y())).toList());
    }
}
