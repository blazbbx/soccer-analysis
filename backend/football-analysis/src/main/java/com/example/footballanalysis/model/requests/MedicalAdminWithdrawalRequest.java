package com.example.footballanalysis.model.requests;

import java.util.UUID;

/** A platform admin's withdrawal on the club's request. The certificate id comes from the break-glass answer. */
public record MedicalAdminWithdrawalRequest(UUID certificateId, String reason) {
}
