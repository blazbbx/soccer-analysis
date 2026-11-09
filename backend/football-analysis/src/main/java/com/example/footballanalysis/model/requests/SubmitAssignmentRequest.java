package com.example.footballanalysis.model.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The whole answer at once: nothing is stored before the hand-in. {@code expectedUpdatedAt} is the homework's update time
 * the page was loaded with, so a page showing questions the coach has changed since gets a 409 and reloads.
 */
public record SubmitAssignmentRequest(
        @Size(max = 10000) String responseText,
        @Size(max = 30) List<@Valid HomeworkAnswerRequest> answers,
        LocalDateTime expectedUpdatedAt
) {}
