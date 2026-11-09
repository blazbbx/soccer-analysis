package com.example.footballanalysis.model.responses;

import java.time.LocalDateTime;
import java.util.UUID;

/** {@code mine} is whether the caller wrote it: the page never marks the caller's own message as new. */
public record HomeworkCommentResponse(
        UUID id,
        String authorFirstName,
        String authorLastName,
        String body,
        LocalDateTime createdAt,
        boolean mine
) {}
