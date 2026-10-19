package com.example.footballanalysis.model.responses;

import java.util.List;
import java.util.UUID;

public record ChildResponse(
        UUID profileId,
        String firstName,
        String lastName,
        UUID linkedUserId,
        List<ChildTeamResponse> teams,
        boolean canOfferRides
) {}
