-- Ellenőrzés, személyes beadás és edzői fájlhozzáférés (docs/sportorvosi-jogszeruseg.md, PR 11).

-- Ki és miből ellenőrizte, hogy az eredmény és a dátum egyezik a papírral. A négy mező együtt töltődik és ürül.
ALTER TABLE medical_certificates ADD COLUMN verified_at timestamp with time zone;
ALTER TABLE medical_certificates ADD COLUMN verified_by_user_id uuid;
ALTER TABLE medical_certificates ADD COLUMN verified_by_role character varying(20);
ALTER TABLE medical_certificates ADD COLUMN verification_method character varying(16);
ALTER TABLE medical_certificates ADD CONSTRAINT fk_medical_certificates_verified_by FOREIGN KEY (verified_by_user_id) REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE medical_certificates ADD CONSTRAINT ck_medical_certificates_verified_by_role CHECK (verified_by_role IN ('CLUB_ADMIN', 'COACH'));
ALTER TABLE medical_certificates ADD CONSTRAINT ck_medical_certificates_verification_method CHECK (verification_method IN ('ORIGINAL', 'FILE'));
ALTER TABLE medical_certificates ADD CONSTRAINT ck_medical_certificates_verified_together CHECK (
    (verified_at IS NULL AND verified_by_role IS NULL AND verification_method IS NULL)
    OR (verified_at IS NOT NULL AND verified_by_role IS NOT NULL AND verification_method IS NOT NULL));

-- Az edző személyesen is beadhat. Az ADMIN a régi sorok miatt marad, új sor ezzel nem keletkezik.
ALTER TABLE medical_certificates DROP CONSTRAINT ck_medical_certificates_role;
ALTER TABLE medical_certificates ADD CONSTRAINT ck_medical_certificates_role CHECK (submitted_by_role IN ('PARENT', 'CLUB_ADMIN', 'ADMIN', 'PLAYER', 'COACH'));

-- A klub engedheti, hogy az edzők a fájlos csapataiknál megnyissák a kitakart másolatot.
ALTER TABLE clubs ADD COLUMN medical_coach_file_access boolean DEFAULT false NOT NULL;
ALTER TABLE clubs ADD COLUMN medical_coach_file_access_acknowledged_at timestamp with time zone;
ALTER TABLE clubs ADD COLUMN medical_coach_file_access_acknowledged_by_user_id uuid;
ALTER TABLE clubs ADD CONSTRAINT fk_clubs_medical_coach_file_access_by FOREIGN KEY (medical_coach_file_access_acknowledged_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

-- A felhasználó törlésénél ellenőrzött FK-k.
CREATE INDEX ix_medical_certificates_verified_by ON medical_certificates (verified_by_user_id);
CREATE INDEX ix_clubs_medical_coach_file_access_by ON clubs (medical_coach_file_access_acknowledged_by_user_id);
