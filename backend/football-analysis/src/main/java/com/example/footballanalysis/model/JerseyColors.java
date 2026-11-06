package com.example.footballanalysis.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Jersey colors as {@code #rrggbb}: home, away, referee, and optionally the two goalkeepers'
 * (only when they wear a different kit than their team). Used for the field-detection worker's
 * suggestion (home / away / referee only, the goalkeepers are never suggested) and for the
 * user-confirmed colors passed on to the ML worker.
 * Members are nullable: a failed suggestion simply carries no colors, and a missing goalkeeper
 * color means "same as the team".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JerseyColors(String home, String away, String referee, String homeGoalkeeper, String awayGoalkeeper) {

    /** The three colors without goalkeeper colors (the suggestion, and teams whose goalkeepers wear the team kit). */
    public JerseyColors(String home, String away, String referee) {
        this(home, away, referee, null, null);
    }
}
