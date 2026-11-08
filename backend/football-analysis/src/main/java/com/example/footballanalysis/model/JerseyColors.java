package com.example.footballanalysis.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Jersey colors as {@code #rrggbb}: home, away, referee. Used for the field-detection worker's
 * suggestion and for the user-confirmed colors passed on to the ML worker.
 * Members are nullable: a failed suggestion simply carries no colors.
 * There is no goalkeeper color: the team classifier is meant to label a person whose kit matches
 * none of these three well enough as the goalkeeper (the threshold is still off, see
 * TeamClassifier.GOALKEEPER_MAX_DELTA_E in the ML worker).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JerseyColors(String home, String away, String referee) {
}
