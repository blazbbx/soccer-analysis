package com.example.footballanalysis.model.responses;

import com.example.footballanalysis.model.db.HomeworkStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * {@code draftRecipientIds} is filled only for a draft. {@code omittedItemCount} is set only on the answer of a copy:
 * the items left out because their clip was deleted.
 */
public record HomeworkDetailResponse(
        UUID id,
        UUID teamId,
        String title,
        String description,
        HomeworkStatus status,
        LocalDateTime dueAt,
        boolean allowLate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean draftEntireRoster,
        List<UUID> draftRecipientIds,
        List<HomeworkItemResponse> items,
        Integer omittedItemCount
) {}
