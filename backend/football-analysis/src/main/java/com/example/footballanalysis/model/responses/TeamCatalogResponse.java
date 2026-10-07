package com.example.footballanalysis.model.responses;

import java.util.List;

/**
 * A katalógus válasza. A listaoldal {@code page}/{@code size} szerint lapoz.
 * Ha {@code partial} igaz, az {@code items} után még van sor: vagy további lap, vagy a választó
 * csak az első szeletet kapta, mert ott nincs lapozó.
 */
public record TeamCatalogResponse(
        List<TeamCatalogItem> items,
        long total,
        boolean partial,
        int page,
        int size,
        int totalPages
) {
}
