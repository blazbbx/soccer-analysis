package com.example.footballanalysis.model.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record UpsertHomeworkRequest(
        @NotBlank(message = "{validation.homework.title}")
        @Size(max = 200, message = "{validation.homework.title}")
        String title,
        @Size(max = 10000) String description,
        LocalDateTime dueAt,
        Boolean allowLate,               // null means true, like the column default
        @NotNull(message = "{validation.homework.items}")
        @Size(max = 30, message = "{validation.homework.items}")
        List<@Valid HomeworkItemRequest> items,
        boolean entireRoster,            // the draft's recipients; PublishHomeworkRequest decides at publish time
        List<UUID> playerProfileIds,     // null keeps the saved list, empty clears it
        LocalDateTime expectedUpdatedAt
) {}
