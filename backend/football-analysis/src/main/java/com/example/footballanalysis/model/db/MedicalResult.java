package com.example.footballanalysis.model.db;

/**
 * The result ticked on the sport medical certificate. Training needs ALKALMAS or VERSENYEZHET, competing needs
 * VERSENYEZHET. The three dated results carry a date (end of validity, or end of the suspension); the two others none.
 */
public enum MedicalResult {
    VERSENYEZHET,
    ALKALMAS,
    NEM_ALKALMAS,
    IDEIGLENESEN_ELTILTVA,
    VEGLEGESEN_ELTILTVA;

    /** VERSENYEZHET or ALKALMAS: the date is the end of validity. */
    public boolean isPositive() {
        return this == VERSENYEZHET || this == ALKALMAS;
    }

    /** NEM_ALKALMAS and VEGLEGESEN_ELTILTVA have no date. */
    public boolean hasDate() {
        return this != NEM_ALKALMAS && this != VEGLEGESEN_ELTILTVA;
    }
}
