package com.example.footballanalysis.model.responses;

import java.util.UUID;

public record TravelPassengerResponse(
        UUID playerProfileId,
        String firstName,
        String lastName
) {}
