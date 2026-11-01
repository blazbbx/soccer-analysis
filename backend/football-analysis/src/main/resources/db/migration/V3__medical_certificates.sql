-- Sportorvosi igazolások (docs/sportorvosi-beadas.md, PR 1 és a klub kapcsolója a PR 2-höz).
-- Egy profilnak legfeljebb egy aktuális igazolása lehet, a függő, lecserélt és visszavont sorok előzmények.

CREATE TABLE medical_certificates (
    id uuid NOT NULL,
    player_profile_id uuid NOT NULL,
    valid_until date NOT NULL,
    object_key character varying(512),
    content_type character varying(100) NOT NULL,
    size_bytes bigint NOT NULL,
    submitted_by_user_id uuid,
    submitted_by_role character varying(20) NOT NULL,
    acknowledged_at timestamp with time zone NOT NULL,
    acknowledgement_version character varying(40) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    superseded_at timestamp with time zone,
    withdrawn_at timestamp with time zone,
    CONSTRAINT medical_certificates_pkey PRIMARY KEY (id),
    CONSTRAINT fk_medical_certificates_profile FOREIGN KEY (player_profile_id) REFERENCES player_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_medical_certificates_submitted_by FOREIGN KEY (submitted_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT ck_medical_certificates_role CHECK (submitted_by_role IN ('PARENT', 'CLUB_ADMIN', 'ADMIN', 'PLAYER')),
    CONSTRAINT ck_medical_certificates_size CHECK (size_bytes BETWEEN 1 AND 10485760)
);

-- Egy profilnak legfeljebb egy aktuális (lezárt, nem lecserélt, nem visszavont) igazolása lehet.
-- Ez egyben az aktuális sor keresőindexe.
CREATE UNIQUE INDEX ux_medical_certificates_current ON medical_certificates (player_profile_id)
    WHERE completed_at IS NOT NULL AND superseded_at IS NULL AND withdrawn_at IS NULL;

-- Előzmény és függő sorok profilonként.
CREATE INDEX ix_medical_certificates_profile_created ON medical_certificates (player_profile_id, created_at);

-- A gyerek saját fiókja csak bekapcsolt joggal adhat le igazolást.
ALTER TABLE player_profiles ADD COLUMN can_upload_medical boolean DEFAULT false NOT NULL;

-- Alapból nem gyűjtünk: minden klub kikapcsolva indul. Bekapcsolni csak elismeréssel lehet.
ALTER TABLE clubs ADD COLUMN medical_mode character varying(8) DEFAULT 'OFF' NOT NULL;
ALTER TABLE clubs ADD CONSTRAINT ck_clubs_medical_mode CHECK (medical_mode IN ('ON', 'OFF'));
ALTER TABLE clubs ADD COLUMN medical_acknowledged_at timestamp with time zone;
ALTER TABLE clubs ADD COLUMN medical_acknowledged_by_user_id uuid;
ALTER TABLE clubs ADD CONSTRAINT fk_clubs_medical_acknowledged_by FOREIGN KEY (medical_acknowledged_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

-- Soha senki nem írta, az aktuális dátum egyetlen forrása az igazolás sora.
ALTER TABLE player_profiles DROP COLUMN medical_valid_until;
