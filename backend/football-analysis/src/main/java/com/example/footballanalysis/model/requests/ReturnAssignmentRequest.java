package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The note is required: the player has to know what to fix. It also goes into the conversation, so it has a comment's limit. */
public record ReturnAssignmentRequest(
        @NotBlank @Size(max = 2000) String note
) {}
