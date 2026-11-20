-- Az engedélyezés egyszerűsítése (docs/sportorvosi-jogszeruseg.md, Engedélyezés az üzemeltetőtől): a platform admin
-- csak feltölti az aláírt PDF-et, az aláírás dátumát és az aláíró nevét nem adja meg. Ami kell, a PDF-ben van.
ALTER TABLE club_medical_agreements DROP COLUMN signed_on;
ALTER TABLE club_medical_agreements DROP COLUMN signatory_name;
