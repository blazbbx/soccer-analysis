-- Életkori szabályok és szülői jelzés (docs/sportorvosi-jogszeruseg.md, PR 12).

-- Szülőnként az utoljára látott aktuális igazolás: ha más adta le, és a szülő még nem nyitotta meg, olvasatlan.
CREATE TABLE medical_parent_seen (
    player_profile_id uuid NOT NULL,
    parent_user_id uuid NOT NULL,
    certificate_id uuid NOT NULL,
    seen_at timestamp with time zone NOT NULL,
    CONSTRAINT medical_parent_seen_pkey PRIMARY KEY (player_profile_id, parent_user_id),
    CONSTRAINT fk_medical_parent_seen_profile FOREIGN KEY (player_profile_id) REFERENCES player_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_medical_parent_seen_parent FOREIGN KEY (parent_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_medical_parent_seen_certificate FOREIGN KEY (certificate_id) REFERENCES medical_certificates(id) ON DELETE CASCADE
);

-- A törlésnél ellenőrzött FK-k.
CREATE INDEX ix_medical_parent_seen_parent ON medical_parent_seen (parent_user_id);
CREATE INDEX ix_medical_parent_seen_certificate ON medical_parent_seen (certificate_id);

-- 18 év felett a szülő csak a játékos engedélyével lát sportorvosi adatot.
ALTER TABLE player_profiles ADD COLUMN medical_parent_access_allowed boolean DEFAULT false NOT NULL;
