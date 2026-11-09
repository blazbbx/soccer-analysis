package com.example.footballanalysis.model.responses;

import java.util.UUID;

/** The clip of a CLIP chat message, enough for the clip card. */
public record ChatClipSummary(
        UUID id,
        String name,
        UUID matchId,
        String matchDisplayName
) {
}
