package com.example.footballanalysis.model.db;

/**
 * The rule that allowed a sport medical submission or verification, not the account's primary role. ADMIN stays for
 * old rows; no new row is written with it. COACH submits only in person, in the date-only mode.
 */
public enum MedicalSubmitterRole {
    PARENT,
    CLUB_ADMIN,
    ADMIN,
    PLAYER,
    COACH
}
