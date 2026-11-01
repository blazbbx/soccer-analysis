package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MedicalAgePolicyTest {

    @Test
    void theEighteenthBirthdayIsTheFirstAdultDay() {
        PlayerProfile profile = new PlayerProfile();
        profile.setDateOfBirth(LocalDate.of(2008, 10, 30));

        assertThat(MedicalAgePolicy.isMinor(profile, LocalDate.of(2026, 10, 29))).isTrue();
        assertThat(MedicalAgePolicy.isMinor(profile, LocalDate.of(2026, 10, 30))).isFalse();
    }

    @Test
    void anEmptyDateOfBirthCountsAsAMinor() {
        assertThat(MedicalAgePolicy.isMinor(new PlayerProfile(), LocalDate.of(2026, 10, 29))).isTrue();
        assertThat(MedicalAgePolicy.isMinor(null, LocalDate.of(2026, 10, 29))).isTrue();
    }
}
