package com.example.footballanalysis.model.responses;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The club tab's list. {@code teamsWithoutCoach}: ON teams where nobody coaches, so the club admin verifies there.
 * {@code counts}: how many children each filter holds, over the whole list (UNVERIFIED, EXPIRING, NOT_COMPETING,
 * BANNED, RESULT_MISSING, MISSING). {@code page}: the filtered list, paged. {@code ready}: the club may collect now
 * (MedicalCompliance#isClubReady); while it may not, it sees no certificate and cannot enter or verify one, but its
 * ON teams still require the certificate, so nothing is deleted.
 */
public record ClubMedicalStatusResponse(
        boolean ready,
        List<TeamRef> teamsWithoutCoach,
        List<TeamRef> teams,
        Map<String, Integer> counts,
        PageResponse<ClubMedicalStatusItem> page
) {
    public record TeamRef(UUID teamId, String name) {}
}
