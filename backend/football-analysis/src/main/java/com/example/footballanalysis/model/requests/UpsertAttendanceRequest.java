package com.example.footballanalysis.model.requests;

import com.example.footballanalysis.model.db.AttendanceStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertAttendanceRequest(
        @NotNull AttendanceStatus status,
        @Size(max = 500) String comment,
        Boolean wantsRide
) {
    @JsonIgnore
    public UpsertAttendanceRequest(AttendanceStatus status, String comment) {
        this(status, comment, null);
    }
}
