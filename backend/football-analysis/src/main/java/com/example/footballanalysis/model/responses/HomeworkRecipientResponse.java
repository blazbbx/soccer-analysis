package com.example.footballanalysis.model.responses;

import java.util.UUID;

/** One player of the roster in the recipient picker. */
public record HomeworkRecipientResponse(
        UUID playerProfileId,
        String firstName,
        String lastName,
        boolean hasLogin
) {}
