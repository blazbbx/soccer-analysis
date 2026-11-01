package com.example.footballanalysis.model.requests;

/**
 * {@code mode} is INHERIT, ON or OFF; {@code evidence} is INHERIT, DATE_ONLY or REDACTED_FILE. At least one of them
 * is required; a null one keeps the stored value. Turning a team ON while its club is not ON needs
 * {@code acknowledged}, the same confirmation as turning the club on.
 */
public record UpdateTeamMedicalModeRequest(String mode, Boolean acknowledged, Boolean confirmDeletion, String evidence) {}
