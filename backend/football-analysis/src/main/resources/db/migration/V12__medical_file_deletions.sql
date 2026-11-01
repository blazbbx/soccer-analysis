-- A törlendő sportorvosi fájlok sora. A sor az igazolás kulcsának nullázásával egy tranzakcióban íródik, és csak a sikeres
-- MinIO-törlés után tűnik el, így egy sikertelen törlést az éjszakai takarítás újra megpróbál.
-- A kulcs csak azonosítókat tartalmaz. Az igazolás azonosítója nem FK: a sor túléli az igazolás és a profil törlését.
CREATE TABLE medical_file_deletions (
    id uuid NOT NULL,
    object_key character varying(512) NOT NULL,
    certificate_id uuid,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT medical_file_deletions_pkey PRIMARY KEY (id)
);
