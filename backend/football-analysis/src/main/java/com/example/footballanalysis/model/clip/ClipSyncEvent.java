package com.example.footballanalysis.model.clip;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record ClipSyncEvent(
        @NotNull(message = "{validation.clip.syncData.t.required}")
        @DecimalMin(value = "0.0", message = "{validation.clip.syncData.t.min}")
        Double t,

        @NotNull(message = "{validation.clip.syncData.type.required}")
        ClipSyncEventType type,

        @NotNull(message = "{validation.clip.syncData.m.required}")
        @DecimalMin(value = "0.0", message = "{validation.clip.syncData.m.min}")
        Double m
) {}

