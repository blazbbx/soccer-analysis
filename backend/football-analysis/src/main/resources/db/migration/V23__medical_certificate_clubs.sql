-- Az igazolás klubhoz kötése (docs/funkciok/sportorvosi/sportorvosi-klubhoz-kotes.md): minden sor megjegyzi, mely
-- kluboknak szól (címzettek). Egy klub csak azt a sort látja, amelynek címzettje, és csak amíg kész és kéri.
--
-- Tömb, nem kapcsolótábla, mint a break-glass napló club_ids oszlopa: nincs rá FK, egy törölt klub azonosítója
-- benne maradhat. A láthatóság mindig a ma élő klubokra számol, így ez nem nyit hozzáférést.
ALTER TABLE medical_certificates ADD COLUMN club_ids uuid[] DEFAULT '{}' NOT NULL;

-- Meglévő sorok: a gyereket most kérő klubok, vagyis a mai viselkedés marad.
UPDATE medical_certificates c SET club_ids = coalesce((
    SELECT array_agg(DISTINCT t.club_id)
    FROM team_roster r
    JOIN teams t ON t.id = r.team_id
    LEFT JOIN clubs cl ON cl.id = t.club_id
    WHERE r.player_profile_id = c.player_profile_id
      AND t.club_id IS NOT NULL
      AND (t.medical_mode = 'ON' OR (t.medical_mode = 'INHERIT' AND cl.medical_mode = 'ON'))), '{}');
