package com.example.footballanalysis.model.requests;

import java.time.LocalDate;

/**
 * Rejects the current certificate. {@code result}, {@code validUntil} and {@code method} work as in
 * {@link MedicalVerificationRequest}; {@code reason} is a {@code MedicalRejectionReason} name.
 */
public record MedicalRejectionRequest(String result, LocalDate validUntil, String method, String reason) {}
