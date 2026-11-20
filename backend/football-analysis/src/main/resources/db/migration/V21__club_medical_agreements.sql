-- A klub aláírt adatfeldolgozási szerződése (docs/sportorvosi-jogszeruseg.md, Engedélyezés az üzemeltetőtől).
-- A platform admin rögzíti, miután a klub jogi képviselője aláírta; ezzel válik a sportorvosi gyűjtés a klub számára
-- elérhetővé. A bekapcsolásról továbbra is a klub dönt a saját feltételeivel.
--
-- A sor a klub törlése után is megmarad (a klub azonosítója NULL lesz, a neve másolatként marad), mert a szerződés
-- az üzemeltető bizonyítéka. A PDF a club-documents vödörben van (agreements/{clubId}/{uuid}.pdf).
CREATE TABLE club_medical_agreements (
    id uuid NOT NULL,
    club_id uuid,
    club_name character varying(255) NOT NULL,
    dpa_version character varying(20) NOT NULL,
    signed_on date NOT NULL,
    signatory_name character varying(200) NOT NULL,
    object_key character varying(512) NOT NULL,
    recorded_at timestamp with time zone NOT NULL,
    recorded_by_user_id uuid,
    -- Kitöltve, ha a szerződés már nem él: WITHDRAWN (a platform admin visszavonta, indoklással) vagy
    -- REPLACED (újabb aláírt szerződés került a helyére).
    ended_at timestamp with time zone,
    ended_by_user_id uuid,
    end_kind character varying(16),
    withdrawal_reason character varying(500),
    CONSTRAINT club_medical_agreements_pkey PRIMARY KEY (id),
    CONSTRAINT fk_club_medical_agreements_club FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE SET NULL,
    CONSTRAINT fk_club_medical_agreements_recorded_by FOREIGN KEY (recorded_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_club_medical_agreements_ended_by FOREIGN KEY (ended_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT ck_club_medical_agreements_end CHECK (
        (ended_at IS NULL AND end_kind IS NULL AND withdrawal_reason IS NULL)
        OR (ended_at IS NOT NULL AND end_kind = 'REPLACED' AND withdrawal_reason IS NULL)
        OR (ended_at IS NOT NULL AND end_kind = 'WITHDRAWN' AND withdrawal_reason IS NOT NULL))
);

-- Klubonként legfeljebb egy élő szerződés.
CREATE UNIQUE INDEX ux_club_medical_agreements_active ON club_medical_agreements (club_id) WHERE ended_at IS NULL;
CREATE INDEX ix_club_medical_agreements_club ON club_medical_agreements (club_id, recorded_at);
-- A felhasználó törlésénél ellenőrzött FK-k.
CREATE INDEX ix_club_medical_agreements_recorded_by ON club_medical_agreements (recorded_by_user_id);
CREATE INDEX ix_club_medical_agreements_ended_by ON club_medical_agreements (ended_by_user_id);

-- Az élő aláírt szerződés verziója a klubon, hogy a feltételek ellenőrzése ne kérdezze le a táblát.
-- NULL, ha nincs élő szerződés; a gyűjtéshez az aktuális szerződésverzióval kell egyeznie.
ALTER TABLE clubs ADD COLUMN medical_agreement_version character varying(20);

-- A klubadmin külön elfogadása megszűnik: a szerződést az aláírt példány bizonyítja (29. döntés).
-- A FK-k és az indexek az oszloppal együtt törlődnek.
ALTER TABLE clubs DROP COLUMN medical_dpa_version;
ALTER TABLE clubs DROP COLUMN medical_dpa_accepted_at;
ALTER TABLE clubs DROP COLUMN medical_dpa_accepted_by_user_id;
-- Ki kapcsolta be és ki engedélyezte az edzőnek: csak beírt, sehol nem olvasott oszlopok. A cselekvő a
-- MEDICAL_SETTINGS_SET auditsorban van; a mikor (..._at) marad, mert a felület kiírja.
ALTER TABLE clubs DROP COLUMN medical_acknowledged_by_user_id;
ALTER TABLE clubs DROP COLUMN medical_coach_file_access_acknowledged_by_user_id;
