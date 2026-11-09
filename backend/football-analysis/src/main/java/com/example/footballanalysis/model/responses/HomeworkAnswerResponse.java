package com.example.footballanalysis.model.responses;

import java.util.List;
import java.util.UUID;

public record HomeworkAnswerResponse(
        UUID itemId,
        String textAnswer,
        List<Integer> selectedOptions,
        Integer timestampMs
) {}
