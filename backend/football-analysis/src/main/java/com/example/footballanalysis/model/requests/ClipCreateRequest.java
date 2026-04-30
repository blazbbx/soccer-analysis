package com.example.footballanalysis.model.requests;

import com.example.footballanalysis.model.clip.ClipSyncEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ClipCreateRequest(
        UUID matchId,

        @Size(max = 255, message = "{validation.clip.name.max}")
        String name,

        List<@Valid ClipSyncEvent> syncData
) {}
