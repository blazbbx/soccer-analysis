package com.example.footballanalysis.model.db;

/**
 * Whether a club or a team collects sport medical certificates. A club is ON or OFF;
 * a team can also follow its club ({@code INHERIT}).
 */
public enum MedicalMode {
    INHERIT,
    ON,
    OFF
}
