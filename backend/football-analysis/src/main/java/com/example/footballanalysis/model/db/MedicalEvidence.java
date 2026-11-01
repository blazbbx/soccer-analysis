package com.example.footballanalysis.model.db;

/**
 * How a club or team asks for proof of the certificate: the result and date only, or also a redacted copy.
 * A club is DATE_ONLY (the default) or REDACTED_FILE; a team can also follow its club ({@code INHERIT}).
 */
public enum MedicalEvidence {
    INHERIT,
    DATE_ONLY,
    REDACTED_FILE
}
