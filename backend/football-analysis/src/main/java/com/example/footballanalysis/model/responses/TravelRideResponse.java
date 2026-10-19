package com.example.footballanalysis.model.responses;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TravelRideResponse(
        UUID id,
        UUID driverUserId,
        String firstName,
        String lastName,
        int seatsTotal,
        int seatsTaken,
        String departurePlace,
        LocalDateTime departureAt,
        LocalDateTime updatedAt,
        String notes,
        List<TravelPassengerResponse> passengers,
        List<TravelPassengerResponse> family
) {}
