package com.example.footballanalysis.model.requests;

import java.time.LocalDate;

/**
 * A coach or a club admin enters the result and the date from the original paper the parent or the child showed in
 * person; {@code seenOriginal} must be true. The row is verified at once.
 */
public record MedicalInPersonRequest(String result, LocalDate validUntil, Boolean seenOriginal) {}
