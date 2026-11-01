package com.example.footballanalysis.model.db;

/** Why a verifier rejected a certificate. A fixed list, so no health data gets into a free text the parent reads. */
public enum MedicalRejectionReason {
    ILLEGIBLE,
    NOT_REDACTED,
    WRONG_PERSON,
    RESULT_MISMATCH,
    OTHER
}
