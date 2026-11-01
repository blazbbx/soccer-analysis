package com.example.footballanalysis.model.requests;

import java.time.LocalDate;

/**
 * Starts a sport medical submission. The service checks every field with its own error key. {@code result} is the
 * result ticked on the paper; {@code validUntil} is required for the three dated results and must be empty for
 * NEM_ALKALMAS and VEGLEGESEN_ELTILTVA. Where the child needs no file, {@code contentType} and {@code sizeBytes}
 * must be left out; where it needs one, they are required and so is {@code redactionConfirmed}.
 */
public record SubmitMedicalCertificateRequest(
        String result,
        LocalDate validUntil,
        Boolean acknowledged,
        String contentType,
        Long sizeBytes,
        Boolean redactionConfirmed
) {}
