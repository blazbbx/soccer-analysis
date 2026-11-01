-- A sportorvosi gyűjtés bekapcsolási feltételei (docs/sportorvosi-jogszeruseg.md, PR 9): a jogszabályhely,
-- a klub adatkezelési tájékoztatója (link vagy feltöltött PDF) és az elfogadott adatfeldolgozási szerződés.

ALTER TABLE clubs ADD COLUMN medical_legal_basis character varying(500);
ALTER TABLE clubs ADD COLUMN medical_notice_url character varying(1000);
ALTER TABLE clubs ADD COLUMN medical_notice_object_key character varying(512);
-- Minden csere növeli; a beadás auditja ezt rögzíti, így tudni, melyik tájékoztatót látta a szülő.
ALTER TABLE clubs ADD COLUMN medical_notice_version integer DEFAULT 0 NOT NULL;
ALTER TABLE clubs ADD COLUMN medical_notice_updated_at timestamp with time zone;
ALTER TABLE clubs ADD COLUMN medical_dpa_version character varying(20);
ALTER TABLE clubs ADD COLUMN medical_dpa_accepted_at timestamp with time zone;
ALTER TABLE clubs ADD COLUMN medical_dpa_accepted_by_user_id uuid;
ALTER TABLE clubs ADD CONSTRAINT fk_clubs_medical_dpa_accepted_by FOREIGN KEY (medical_dpa_accepted_by_user_id) REFERENCES users(id) ON DELETE SET NULL;
-- A tájékoztató vagy link, vagy feltöltött PDF, a kettő egyszerre nem.
ALTER TABLE clubs ADD CONSTRAINT ck_clubs_medical_notice_one CHECK (medical_notice_url IS NULL OR medical_notice_object_key IS NULL);

-- A felhasználó törlésénél ellenőrzött FK.
CREATE INDEX ix_clubs_medical_dpa_accepted_by ON clubs (medical_dpa_accepted_by_user_id);
