package com.example.footballanalysis.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One goal as four corners in the order the goal model was trained with:
 * P1 top-left, P2 top-right, P3 bottom-left, P4 bottom-right.
 *
 * <p>Note: this is a different order from the field corners (top-left, top-right, bottom-right,
 * bottom-left). The goal polygon is therefore P1, P2, P4, P3.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Goal(List<GoalCorner> corners) {

    public static final int CORNER_COUNT = 4;

    /** True when there are exactly four corners and every one has a position. */
    public boolean isComplete() {
        return corners != null
                && corners.size() == CORNER_COUNT
                && corners.stream().allMatch(c -> c != null && c.isPlaced());
    }
}
