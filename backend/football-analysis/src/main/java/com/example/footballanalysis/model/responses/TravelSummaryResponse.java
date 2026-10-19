package com.example.footballanalysis.model.responses;

public record TravelSummaryResponse(
        int goingCount,
        int notGoingCount,
        int maybeCount,
        int noResponseCount,
        int seatsTotal,
        int capacityGap,
        int unseatedGoingCount
) {}
