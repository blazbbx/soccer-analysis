package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record UpsertRideOfferRequest(
        @Min(value = 1, message = "{validation.travel.seats_total}")
        @Max(value = 8, message = "{validation.travel.seats_total}")
        int seatsTotal,
        @Size(max = 255) String departurePlace,
        LocalDateTime departureAt,
        @Size(max = 500) String notes,
        LocalDateTime expectedUpdatedAt
) {}
