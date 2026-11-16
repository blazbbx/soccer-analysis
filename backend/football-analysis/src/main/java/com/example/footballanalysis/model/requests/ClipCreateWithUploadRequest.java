package com.example.footballanalysis.model.requests;

import com.example.footballanalysis.model.clip.ClipSyncEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
        String timelineFilename,

        // File sizes in bytes, signed into the upload URLs. No audio size means the clip has no audio.
        @NotNull(message = "{validation.upload.size.required}")
        @Positive(message = "{validation.upload.size.required}")
        Long overlaySize,

        @Positive(message = "{validation.upload.size.required}")
        Long audioSize,

        @NotNull(message = "{validation.upload.size.required}")
        @Positive(message = "{validation.upload.size.required}")
        Long timelineSize
) {}
