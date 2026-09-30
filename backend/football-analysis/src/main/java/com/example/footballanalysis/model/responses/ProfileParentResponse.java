package com.example.footballanalysis.model.responses;

import java.util.UUID;

public record ProfileParentResponse(
        UUID id,
        String firstName,
        String lastName,
        String email
) {}
