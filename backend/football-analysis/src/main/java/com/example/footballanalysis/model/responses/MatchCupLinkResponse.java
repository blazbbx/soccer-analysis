package com.example.footballanalysis.model.responses;

import java.util.UUID;

/** A cup match that an analysed match is linked to. Shown as a chip on the match page. */
public record MatchCupLinkResponse(
        UUID cupId,
        String cupName,
        UUID cupMatchId
) {
}
