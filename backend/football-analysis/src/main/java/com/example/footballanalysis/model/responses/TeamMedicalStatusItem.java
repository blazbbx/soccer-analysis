package com.example.footballanalysis.model.responses;

import java.time.LocalDate;
import java.util.UUID;

/** The two result flags are what the paper says, shown only with the status sentence; never a permission. */
public record TeamMedicalStatusItem(
        UUID profileId,
        String status,
        String result,
        Boolean resultAllowsTraining,
        Boolean resultAllowsCompeting,
        LocalDate validUntil,
        Integer daysRemaining,
        boolean verified
) {}
