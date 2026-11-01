-- A platform admin sportorvosi hozzáférései indoklással (docs/sportorvosi-jogszeruseg.md, PR 8).

-- Egy sor egy break-glass olvasás, megnyitás vagy visszavonás. A profilra nem mutat FK, és a gyerek nevét
-- is rögzíti, hogy a profil törlése után is olvasható maradjon az auditmegőrzés idejéig.
CREATE TABLE medical_admin_accesses (
    id uuid NOT NULL,
    certificate_id uuid,
    player_profile_id uuid NOT NULL,
    player_name character varying(255) NOT NULL,
    club_ids uuid[] DEFAULT '{}' NOT NULL,
    admin_user_id uuid,
    action character varying(20) NOT NULL,
    reason character varying(500) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT medical_admin_accesses_pkey PRIMARY KEY (id),
    CONSTRAINT fk_medical_admin_accesses_certificate FOREIGN KEY (certificate_id) REFERENCES medical_certificates(id) ON DELETE SET NULL,
    CONSTRAINT fk_medical_admin_accesses_admin FOREIGN KEY (admin_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT ck_medical_admin_accesses_action CHECK (action IN ('READ', 'FILE_OPENED', 'WITHDRAWN'))
);

-- A klub listája a club_ids-ra keres, a takarító a created_at-re, a szülői tartalék a profilra.
CREATE INDEX ix_medical_admin_accesses_club_ids ON medical_admin_accesses USING gin (club_ids);
CREATE INDEX ix_medical_admin_accesses_created ON medical_admin_accesses (created_at);
CREATE INDEX ix_medical_admin_accesses_profile ON medical_admin_accesses (player_profile_id);
-- A törlésnél ellenőrzött FK-k.
CREATE INDEX ix_medical_admin_accesses_certificate ON medical_admin_accesses (certificate_id);
CREATE INDEX ix_medical_admin_accesses_admin ON medical_admin_accesses (admin_user_id);

-- Azok a klubok, ahol a gyereknek utoljára kötelező volt a sportorvosi. Üres, amíg kötelező.
ALTER TABLE player_profiles ADD COLUMN medical_last_required_club_ids uuid[] DEFAULT '{}' NOT NULL;
