package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.MedicalResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.example.footballanalysis.service.MedicalStatus.State;
import static org.assertj.core.api.Assertions.assertThat;

class MedicalStatusTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

    @Test
    void missingCertificateHasNoDayCountAndAllowsNothing() {
        MedicalStatus status = MedicalStatus.of(false, null, null, false, TODAY);
        assertThat(status).isEqualTo(new MedicalStatus(State.MISSING, null, false, false));
    }

    @Test
    void aPositiveResultYesterdayIsExpiredWithMinusOneAndBothFlagsFalse() {
        assertThat(of(MedicalResult.VERSENYEZHET, TODAY.minusDays(1), true))
                .isEqualTo(new MedicalStatus(State.EXPIRED, -1, false, false));
    }

    @Test
    void todayAndTheThirtiethDayAreExpiringTheThirtyFirstIsValid() {
        assertThat(of(MedicalResult.VERSENYEZHET, TODAY, true)).isEqualTo(new MedicalStatus(State.EXPIRING, 0, true, true));
        assertThat(of(MedicalResult.VERSENYEZHET, TODAY.plusDays(30), true)).isEqualTo(new MedicalStatus(State.EXPIRING, 30, true, true));
        assertThat(of(MedicalResult.VERSENYEZHET, TODAY.plusDays(31), true)).isEqualTo(new MedicalStatus(State.VALID, 31, true, true));
    }

    @Test
    void alkalmasAllowsTrainingButNotCompeting() {
        assertThat(of(MedicalResult.ALKALMAS, TODAY.plusDays(100), true)).isEqualTo(new MedicalStatus(State.VALID, 100, true, false));
        assertThat(of(MedicalResult.ALKALMAS, TODAY.plusDays(5), true)).isEqualTo(new MedicalStatus(State.EXPIRING, 5, true, false));
    }

    @Test
    void anUnverifiedPositiveRowIsUnverifiedUntilItExpiresWithTheFlagsFromTheResult() {
        assertThat(of(MedicalResult.VERSENYEZHET, TODAY.plusDays(100), false)).isEqualTo(new MedicalStatus(State.UNVERIFIED, 100, true, true));
        assertThat(of(MedicalResult.ALKALMAS, TODAY.plusDays(10), false)).isEqualTo(new MedicalStatus(State.UNVERIFIED, 10, true, false));
        assertThat(of(MedicalResult.ALKALMAS, TODAY.minusDays(3), false)).isEqualTo(new MedicalStatus(State.EXPIRED, -3, false, false));
    }

    @Test
    void aTemporarySuspensionIsSuspendedUntilItsDateThenExpiredVerifiedOrNot() {
        assertThat(of(MedicalResult.IDEIGLENESEN_ELTILTVA, TODAY.plusDays(40), false)).isEqualTo(new MedicalStatus(State.SUSPENDED, 40, false, false));
        assertThat(of(MedicalResult.IDEIGLENESEN_ELTILTVA, TODAY, true)).isEqualTo(new MedicalStatus(State.SUSPENDED, 0, false, false));
        assertThat(of(MedicalResult.IDEIGLENESEN_ELTILTVA, TODAY.minusDays(1), true)).isEqualTo(new MedicalStatus(State.EXPIRED, -1, false, false));
    }

    @Test
    void notFitAndPermanentlyBannedAreNotFitWithoutADayCountEvenUnverified() {
        assertThat(of(MedicalResult.NEM_ALKALMAS, null, false)).isEqualTo(new MedicalStatus(State.NOT_FIT, null, false, false));
        assertThat(of(MedicalResult.VEGLEGESEN_ELTILTVA, null, true)).isEqualTo(new MedicalStatus(State.NOT_FIT, null, false, false));
    }

    @Test
    void anOldRowWithoutAResultIsResultMissingWithEmptyFlags() {
        assertThat(of(null, TODAY.plusDays(100), true)).isEqualTo(new MedicalStatus(State.RESULT_MISSING, null, null, null));
    }

    @Test
    void onlyAGoodPositiveRowIsStillGood() {
        assertThat(of(MedicalResult.VERSENYEZHET, TODAY.plusDays(100), true).isStillGood()).isTrue();
        assertThat(of(MedicalResult.ALKALMAS, TODAY.plusDays(100), false).isStillGood()).isTrue();
        assertThat(of(MedicalResult.VERSENYEZHET, TODAY.minusDays(1), true).isStillGood()).isFalse();
        assertThat(of(MedicalResult.IDEIGLENESEN_ELTILTVA, TODAY.plusDays(10), true).isStillGood()).isFalse();
    }

    private static MedicalStatus of(MedicalResult result, LocalDate validUntil, boolean verified) {
        return MedicalStatus.of(true, result, validUntil, verified, TODAY);
    }
}
