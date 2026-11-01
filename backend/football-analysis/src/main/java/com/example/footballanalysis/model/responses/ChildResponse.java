package com.example.footballanalysis.model.responses;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ChildResponse(
        UUID profileId,
        String firstName,
        String lastName,
        UUID linkedUserId,
        List<ChildTeamResponse> teams,
        boolean canOfferRides,
        boolean medicalRequired,
        String medicalStatus,
        String medicalResult,
        Boolean medicalResultAllowsTraining,
        Boolean medicalResultAllowsCompeting,
        LocalDate medicalValidUntil,
        Integer medicalDaysRemaining,
        Boolean medicalVerified,
        boolean medicalUnseen,
        boolean medicalAdultManaged,
        boolean canUploadMedical
) {}
