package com.example.footballanalysis.model.responses;

import java.util.List;

/**
 * {@code enabled} is the team's effective mode. {@code mode} (INHERIT, ON, OFF) and {@code evidence}
 * (INHERIT, DATE_ONLY, REDACTED_FILE) are its own settings.
 */
public record TeamMedicalStatusResponse(boolean enabled, String mode, String evidence, List<TeamMedicalStatusItem> items) {}
