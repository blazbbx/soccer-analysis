-- A platform admin nem nyithat fájlt, csak technikai adatot lát és visszavonhat (docs/sportorvosi-jogszeruseg.md, Platform admin).

-- A régi megnyitási sorok törlődnek, nem READ-re íródnak át: a napló nem állíthatja, hogy csak technikai adatot látott.
-- Éles klub még nincs, ez csak fejlesztői adatot érint. Az audit_events sorai megmaradnak.
DELETE FROM medical_admin_accesses WHERE action = 'FILE_OPENED';

ALTER TABLE medical_admin_accesses DROP CONSTRAINT ck_medical_admin_accesses_action;
ALTER TABLE medical_admin_accesses ADD CONSTRAINT ck_medical_admin_accesses_action CHECK (action IN ('READ', 'WITHDRAWN'));
