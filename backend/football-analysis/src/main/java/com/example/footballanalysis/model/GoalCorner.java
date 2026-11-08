package com.example.footballanalysis.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One corner of a detected or confirmed goal, in pixels of the defished preview frame (the same
 * frame the field corners are placed on).
 *
 * <p>{@code x} and {@code y} are {@code null} when the goal model had too little confidence in that
 * corner: the worker then reports {@code source = "needs_manual_review"} and deliberately does not
 * guess a position (docs/ai-collaboration-log.md, 2026-09-27, 5. döntés) — the user places it.
 *
 * <p>{@code source}: {@code detected}, {@code clamped} (clipped to the frame edge),
 * {@code needs_manual_review} (worker output) or {@code confirmed} (the user's final value).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoalCorner(Integer x, Integer y, String source) {

    public static final String SOURCE_CONFIRMED = "confirmed";

    public static GoalCorner confirmed(int x, int y) {
        return new GoalCorner(x, y, SOURCE_CONFIRMED);
    }

    public boolean isPlaced() {
        return x != null && y != null;
    }
}
