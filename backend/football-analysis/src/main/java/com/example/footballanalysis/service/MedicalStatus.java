package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.MedicalResult;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The sport medical light, computed on every read from the current certificate's result, {@code validUntil},
 * whether someone verified it, and today's date in the application zone (docs/sportorvosi-jogszeruseg.md, Eredmény).
 * For a positive result the 30th day is still EXPIRING, the 31st is VALID; an unverified positive row is UNVERIFIED
 * until it expires. Negative results show as the paper says, verified or not.
 * <p>
 * {@code resultAllowsTraining} and {@code resultAllowsCompeting} say what the paper states, not what the child may do.
 * They are shown only together with the status sentence, and no permission or ban (roster, attendance, entry) may
 * be built on them: the light is a warning, not a block.
 */
public record MedicalStatus(State status, Integer daysRemaining, Boolean resultAllowsTraining, Boolean resultAllowsCompeting) {

    public static final int EXPIRING_WITHIN_DAYS = 30;

    public enum State {
        MISSING,
        RESULT_MISSING,
        UNVERIFIED,
        EXPIRED,
        EXPIRING,
        VALID,
        SUSPENDED,
        NOT_FIT
    }

    /** A row from before the result existed counts as verified: verification did not exist either. */
    public static MedicalStatus of(boolean current, MedicalResult result, LocalDate validUntil, boolean verified, LocalDate today) {
        if (!current) {
            return new MedicalStatus(State.MISSING, null, false, false);
        }
        if (result == null) {
            return new MedicalStatus(State.RESULT_MISSING, null, null, null);
        }
        if (!result.hasDate()) {
            return new MedicalStatus(State.NOT_FIT, null, false, false);
        }
        int days = (int) ChronoUnit.DAYS.between(today, validUntil);
        if (days < 0) {
            return new MedicalStatus(State.EXPIRED, days, false, false);
        }
        if (result == MedicalResult.IDEIGLENESEN_ELTILTVA) {
            return new MedicalStatus(State.SUSPENDED, days, false, false);
        }
        boolean competing = result == MedicalResult.VERSENYEZHET;
        if (!verified) {
            return new MedicalStatus(State.UNVERIFIED, days, true, competing);
        }
        return new MedicalStatus(days <= EXPIRING_WITHIN_DAYS ? State.EXPIRING : State.VALID, days, true, competing);
    }

    /** VALID, EXPIRING or UNVERIFIED: a positive certificate that has not run out. A past positive date must not replace it. */
    public boolean isStillGood() {
        return status == State.VALID || status == State.EXPIRING || status == State.UNVERIFIED;
    }
}
