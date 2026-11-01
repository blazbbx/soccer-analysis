package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.PlayerProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Parental authority lasts until 18 (docs/sportorvosi-jogszeruseg.md, Életkor). The age comes from the profile's date
 * of birth and today's date of the application clock, on every request; nothing is scheduled. An empty date of birth
 * counts as a minor: that is the safe direction.
 */
@Component
@RequiredArgsConstructor
public class MedicalAgePolicy {

    public static final int ADULT_AGE = 18;

    private final Clock clock;

    public boolean isMinor(PlayerProfile profile) {
        return isMinor(profile, LocalDate.now(clock));
    }

    /** The 18th birthday is the first adult day. */
    public static boolean isMinor(PlayerProfile profile, LocalDate today) {
        if (profile == null || profile.getDateOfBirth() == null) {
            return true;
        }
        return today.isBefore(profile.getDateOfBirth().plusYears(ADULT_AGE));
    }
}
