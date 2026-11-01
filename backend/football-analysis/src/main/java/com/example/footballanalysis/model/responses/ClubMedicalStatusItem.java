package com.example.footballanalysis.model.responses;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One child on the club's sport medical list: every ON team of the club, each child once. The result flags are what
 * the paper says, shown only with the status sentence. {@code hasFile} only where this club asks for the file.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClubMedicalStatusItem(
        UUID profileId,
        String firstName,
        String lastName,
        List<UUID> teamIds,
        String status,
        String result,
        Boolean resultAllowsTraining,
        Boolean resultAllowsCompeting,
        LocalDate validUntil,
        Integer daysRemaining,
        boolean verified,
        UUID certificateId,
        Boolean hasFile,
        boolean fileRequired
) {}
