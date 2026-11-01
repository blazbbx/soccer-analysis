-- Sportorvosi beállítások csapatra és megőrzés (docs/sportorvosi-beadas.md, PR 6).

-- Az előzmény megőrzése klubonként: 1, 3 vagy 5 év.
ALTER TABLE clubs ADD COLUMN medical_history_retention_years integer DEFAULT 3 NOT NULL;
ALTER TABLE clubs ADD CONSTRAINT ck_clubs_medical_retention CHECK (medical_history_retention_years IN (1, 3, 5));

-- A csapat alapból a klubját követi.
ALTER TABLE teams ADD COLUMN medical_mode character varying(8) DEFAULT 'INHERIT' NOT NULL;
ALTER TABLE teams ADD CONSTRAINT ck_teams_medical_mode CHECK (medical_mode IN ('INHERIT', 'ON', 'OFF'));

-- Mióta nem kötelező a gyereknek a sportorvosi (üres, amíg kötelező), és a kötelező klubok leghosszabb megőrzése.
ALTER TABLE player_profiles ADD COLUMN medical_not_required_since timestamp with time zone;
ALTER TABLE player_profiles ADD COLUMN medical_retention_years integer;
