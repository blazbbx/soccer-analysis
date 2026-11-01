package com.example.footballanalysis.model.requests;

import java.util.UUID;

/** The current certificate the parent just saw on the child's profile. */
public record MedicalSeenRequest(UUID certificateId) {}
