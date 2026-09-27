ALTER TABLE player_profiles DROP CONSTRAINT IF EXISTS fk_player_profiles_club;
DROP INDEX IF EXISTS ix_player_profiles_club_id;
ALTER TABLE player_profiles DROP COLUMN IF EXISTS club_id;
