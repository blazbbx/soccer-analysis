package com.example.footballanalysis.model.responses;

import java.time.LocalDateTime;
import java.util.UUID;

/** A clip shared in a team chat: the latest message that shared it. */
public record ChatSharedClipResponse(
        UUID messageId,
        LocalDateTime sharedAt,
        String sharedByName,
        ChatClipSummary clip
) {
}
