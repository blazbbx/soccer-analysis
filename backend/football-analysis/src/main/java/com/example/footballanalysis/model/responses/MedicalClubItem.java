package com.example.footballanalysis.model.responses;

import java.util.UUID;

/** A club that sees the current certificate, for the parent's "Látja: …" line. */
public record MedicalClubItem(UUID clubId, String clubName) {}
