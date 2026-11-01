package com.example.footballanalysis.model.responses;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * One break-glass access of a platform admin. The club's list carries the admin's and the child's name;
 * the parent's copy inside the child's state carries only the time, the action and the reason.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MedicalAdminAccessItem(
        UUID id,
        Instant createdAt,
        String adminFirstName,
        String adminLastName,
        String playerName,
        String action,
        String reason
) {
}
