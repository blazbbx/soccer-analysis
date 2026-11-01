package com.example.footballanalysis.model.requests;

import java.time.LocalDate;

/**
 * Corrects the date of the current certificate from the paper and verifies it. {@code result}, {@code validUntil} and
 * {@code method} work as in {@link MedicalVerificationRequest}; {@code correctedValidUntil} is the date on the paper.
 */
public record MedicalDateCorrectionRequest(String result, LocalDate validUntil, String method, LocalDate correctedValidUntil) {}
