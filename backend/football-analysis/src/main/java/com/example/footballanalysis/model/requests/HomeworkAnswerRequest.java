package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record HomeworkAnswerRequest(
        @NotNull UUID itemId,
        @Size(max = 10000) String textAnswer,
        @Size(max = 6) List<@PositiveOrZero Integer> selectedOptions,
        @PositiveOrZero Integer timestampMs
) {}
