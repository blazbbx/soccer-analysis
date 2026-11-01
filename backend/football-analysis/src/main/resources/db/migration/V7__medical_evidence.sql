-- Az igazolás eredménye és a bizonyítási mód (docs/sportorvosi-jogszeruseg.md, PR 10).

-- Bizonyítási mód: a klubon csak dátum vagy kitakart másolat, a csapat alapból a klubját követi.
ALTER TABLE clubs ADD COLUMN medical_evidence character varying(16) DEFAULT 'DATE_ONLY' NOT NULL;
ALTER TABLE clubs ADD CONSTRAINT ck_clubs_medical_evidence CHECK (medical_evidence IN ('DATE_ONLY', 'REDACTED_FILE'));
-- A már bekapcsolt klubok fájlt gyűjtöttek, hogy a meglévő fájlok ne veszítsék el a gazdájukat.
UPDATE clubs SET medical_evidence = 'REDACTED_FILE' WHERE medical_mode = 'ON';
ALTER TABLE teams ADD COLUMN medical_evidence character varying(16) DEFAULT 'INHERIT' NOT NULL;
ALTER TABLE teams ADD CONSTRAINT ck_teams_medical_evidence CHECK (medical_evidence IN ('INHERIT', 'DATE_ONLY', 'REDACTED_FILE'));

-- Egy igazolássor dátumos (fájl nélkül) vagy fájlos. A meglévő sorok mind fájlosak.
ALTER TABLE medical_certificates ADD COLUMN evidence character varying(16) DEFAULT 'FILE' NOT NULL;
ALTER TABLE medical_certificates ALTER COLUMN evidence DROP DEFAULT;
ALTER TABLE medical_certificates ADD CONSTRAINT ck_medical_certificates_evidence CHECK (evidence IN ('DATE_ONLY', 'FILE'));
ALTER TABLE medical_certificates ALTER COLUMN content_type DROP NOT NULL;
ALTER TABLE medical_certificates ALTER COLUMN size_bytes DROP NOT NULL;
ALTER TABLE medical_certificates ADD CONSTRAINT ck_medical_certificates_content_type CHECK (evidence = 'DATE_ONLY' OR content_type IS NOT NULL);
ALTER TABLE medical_certificates DROP CONSTRAINT ck_medical_certificates_size;
ALTER TABLE medical_certificates ADD CONSTRAINT ck_medical_certificates_size CHECK (evidence <> 'FILE' OR size_bytes BETWEEN 1 AND 10485760);

-- A papíron bejelölt minősítés. A régi sorokra nem találunk ki eredményt, új sor csak eredménnyel jöhet létre.
ALTER TABLE medical_certificates ADD COLUMN result character varying(24);
ALTER TABLE medical_certificates ADD CONSTRAINT ck_medical_certificates_result CHECK (result IN
    ('VERSENYEZHET', 'ALKALMAS', 'NEM_ALKALMAS', 'IDEIGLENESEN_ELTILTVA', 'VEGLEGESEN_ELTILTVA'));
ALTER TABLE medical_certificates ADD CONSTRAINT ck_medical_certificates_result_required CHECK (result IS NOT NULL OR acknowledgement_version = 'sport-medical-v1');
-- A három dátumos eredményhez kötelező a dátum, a két dátum nélkülihez tilos. A régi sorokon megvan a dátum.
ALTER TABLE medical_certificates ALTER COLUMN valid_until DROP NOT NULL;
ALTER TABLE medical_certificates ADD CONSTRAINT ck_medical_certificates_valid_until CHECK (
    (result IS NULL AND valid_until IS NOT NULL)
    OR (result IS NOT NULL AND (result IN ('NEM_ALKALMAS', 'VEGLEGESEN_ELTILTVA')) = (valid_until IS NULL)));

-- Mióta nem kell a gyereknek fájl, bár van neki (üres, amíg kell, vagy amíg nincs fájlja). A takarító 30 nap után törli a fájlt.
ALTER TABLE player_profiles ADD COLUMN medical_file_not_required_since timestamp with time zone;
