package com.example.footballanalysis.model.responses;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MedicalHistoryItemResponse(
        String result,
        LocalDate validUntil,
        Instant submittedAt,
        String submittedByFirstName,
        String submittedByLastName,
        String submittedByRole,
        Instant supersededAt,
        Instant withdrawnAt,
        Instant verifiedAt,
        Instant rejectedAt
) {}
