package com.example.footballanalysis.model.responses;

import java.time.LocalDateTime;

/** The numbers on the menu: the recipient's open and overdue work, or the staff's hand-ins waiting for a review. */
public record HomeworkSummaryCountsResponse(
        long open,
        long overdue,
        long toReview,
        LocalDateTime nextDueAt
) {
    public static HomeworkSummaryCountsResponse empty() {
        return new HomeworkSummaryCountsResponse(0, 0, 0, null);
    }
}
