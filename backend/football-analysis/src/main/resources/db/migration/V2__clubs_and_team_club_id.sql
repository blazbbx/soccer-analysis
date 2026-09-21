-- Club tenant. Adds teams.club_id when the Hibernate-created teams table
-- already exists (typical local Postgres). Leftover rows may keep a null
-- club_id. New teams always set club_id in the app.
CREATE TABLE IF NOT EXISTS clubs (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    short_name VARCHAR(255),
    logo_url VARCHAR(1024),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE teams ADD COLUMN IF NOT EXISTS club_id UUID;

CREATE INDEX IF NOT EXISTS ix_teams_club_id ON teams (club_id);
