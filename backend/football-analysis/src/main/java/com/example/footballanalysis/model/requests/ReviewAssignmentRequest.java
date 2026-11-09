package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.Size;

public record ReviewAssignmentRequest(
        @Size(max = 5000) String feedback
) {}
