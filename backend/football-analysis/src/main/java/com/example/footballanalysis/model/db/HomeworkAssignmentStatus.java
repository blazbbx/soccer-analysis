package com.example.footballanalysis.model.db;

public enum HomeworkAssignmentStatus {
    ASSIGNED,
    SUBMITTED,
    /** The coach sent the work back for a fix: on the to-do list again, and the deadline no longer closes it. */
    RETURNED,
    COMPLETED
}
