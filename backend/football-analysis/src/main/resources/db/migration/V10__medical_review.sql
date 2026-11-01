-- Visszautasítás és dátumjavítás az ellenőrzésnél (docs/sportorvosi-jogszeruseg.md, Ellenőrzés).

-- A visszautasított sor visszavont sor is: a fájlja törlődik, az ok listából jön, így nem kerül bele szabad szöveg.
ALTER TABLE medical_certificates ADD COLUMN rejected_at timestamp with time zone;
ALTER TABLE medical_certificates ADD COLUMN rejected_by_user_id uuid;
ALTER TABLE medical_certificates ADD COLUMN rejected_by_role character varying(20);
ALTER TABLE medical_certificates ADD COLUMN rejection_reason character varying(24);
ALTER TABLE medical_certificates ADD CONSTRAINT fk_medical_certificates_rejected_by FOREIGN KEY (rejected_by_user_id) REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE medical_certificates ADD CONSTRAINT ck_medical_certificates_rejected_by_role CHECK (rejected_by_role IN ('CLUB_ADMIN', 'COACH'));
ALTER TABLE medical_certificates ADD CONSTRAINT ck_medical_certificates_rejection_reason CHECK (rejection_reason IN ('ILLEGIBLE', 'NOT_REDACTED', 'WRONG_PERSON', 'RESULT_MISMATCH', 'OTHER'));
ALTER TABLE medical_certificates ADD CONSTRAINT ck_medical_certificates_rejected_together CHECK (
    (rejected_at IS NULL AND rejected_by_role IS NULL AND rejection_reason IS NULL)
    OR (rejected_at IS NOT NULL AND rejected_by_role IS NOT NULL AND rejection_reason IS NOT NULL AND withdrawn_at IS NOT NULL));

-- Ki javította a dátumot a papír alapján, és mi volt az eredetileg beírt érték; a szülő ezt látja.
ALTER TABLE medical_certificates ADD COLUMN date_corrected_at timestamp with time zone;
ALTER TABLE medical_certificates ADD COLUMN date_corrected_by_user_id uuid;
ALTER TABLE medical_certificates ADD COLUMN date_corrected_by_role character varying(20);
ALTER TABLE medical_certificates ADD COLUMN original_valid_until date;
ALTER TABLE medical_certificates ADD CONSTRAINT fk_medical_certificates_date_corrected_by FOREIGN KEY (date_corrected_by_user_id) REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE medical_certificates ADD CONSTRAINT ck_medical_certificates_date_corrected_by_role CHECK (date_corrected_by_role IN ('CLUB_ADMIN', 'COACH'));
ALTER TABLE medical_certificates ADD CONSTRAINT ck_medical_certificates_date_corrected_together CHECK (
    (date_corrected_at IS NULL AND date_corrected_by_role IS NULL AND original_valid_until IS NULL)
    OR (date_corrected_at IS NOT NULL AND date_corrected_by_role IS NOT NULL AND original_valid_until IS NOT NULL));

-- A felhasználó törlésénél ellenőrzött FK-k.
CREATE INDEX ix_medical_certificates_rejected_by ON medical_certificates (rejected_by_user_id);
CREATE INDEX ix_medical_certificates_date_corrected_by ON medical_certificates (date_corrected_by_user_id);
