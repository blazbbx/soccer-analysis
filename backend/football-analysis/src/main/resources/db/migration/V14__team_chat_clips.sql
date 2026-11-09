-- Klip megosztása a csapat chatben: az üzenet típusa (TEXT vagy CLIP) és a megosztott klip.
-- A klip törlésekor a clip_id NULL lesz, a type CLIP marad, így a felület tudja, hogy „A klip már nem érhető el”.
-- A meglévő üzenetek TEXT típusúak lesznek (DEFAULT), más backfill nincs.
ALTER TABLE team_messages
    ADD COLUMN type    varchar(16) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN clip_id uuid NULL;

ALTER TABLE team_messages
    ADD CONSTRAINT ck_team_messages_type CHECK (type IN ('TEXT', 'CLIP')),
    ADD CONSTRAINT fk_team_messages_clip FOREIGN KEY (clip_id) REFERENCES clips (id) ON DELETE SET NULL;

-- FK-index a klip törléséhez (a V2__foreign_key_indexes mintájára)
CREATE INDEX ix_team_messages_clip ON team_messages (clip_id) WHERE clip_id IS NOT NULL;
