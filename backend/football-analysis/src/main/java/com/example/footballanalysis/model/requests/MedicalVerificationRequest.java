package com.example.footballanalysis.model.requests;

import java.time.LocalDate;

/**
 * Marks the current certificate as checked against the paper. {@code result} and {@code validUntil} are what the
 * verifier saw on the screen, so a certificate replaced meanwhile is not confirmed. {@code method} is ORIGINAL (the
 * paper in person) or FILE (the uploaded copy, opened by the caller within the last 30 minutes).
 */
public record MedicalVerificationRequest(String result, LocalDate validUntil, String method) {}
