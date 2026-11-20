package com.example.footballanalysis.model.requests;

import java.util.UUID;

/**
 * The parent (or the adult player) gives the current certificate to one more requiring and ready club;
 * {@code acknowledged} must be true: that club's privacy notice was shown and accepted.
 */
public record MedicalShareRequest(UUID clubId, Boolean acknowledged) {}
