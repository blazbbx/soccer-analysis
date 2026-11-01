package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.NotNull;

public record SetMedicalUploadRightRequest(@NotNull Boolean allowed) {}
