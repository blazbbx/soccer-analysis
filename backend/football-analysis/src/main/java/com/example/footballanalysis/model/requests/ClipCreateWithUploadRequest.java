package com.example.footballanalysis.model.requests;

import com.example.footballanalysis.model.clip.ClipSyncEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ClipCreateWithUploadRequest(
        
        UUID matchId,

        @Size(max = 255, message = "{validation.clip.name.max}")
        String name,

        List<@Valid ClipSyncEvent> syncData,

        @Size(max = 255, message = "{validation.clip.overlayFilename.max}")
        String overlayFilename,

        @Size(max = 255, message = "{validation.clip.audioFilename.max}")
        String audioFilename,

        @Size(max = 255, message = "{validation.clip.timelineFilename.max}")
        String timelineFilename
) {}
