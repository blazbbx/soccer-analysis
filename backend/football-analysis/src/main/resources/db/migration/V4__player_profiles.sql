CREATE TABLE IF NOT EXISTS player_teams (
    player_id UUID NOT NULL,
    team_id UUID NOT NULL
);

CREATE TABLE IF NOT EXISTS player_profiles (
    id UUID PRIMARY KEY,
    club_id UUID NOT NULL,
    team_id UUID,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    date_of_birth DATE,
    linked_user_id UUID,
    medical_valid_until DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_player_profiles_club FOREIGN KEY (club_id) REFERENCES clubs (id),
    CONSTRAINT fk_player_profiles_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_player_profiles_user FOREIGN KEY (linked_user_id) REFERENCES users (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_player_profiles_linked_user ON player_profiles (linked_user_id);

CREATE INDEX IF NOT EXISTS ix_player_profiles_team_id ON player_profiles (team_id);

CREATE INDEX IF NOT EXISTS ix_player_profiles_club_id ON player_profiles (club_id);

INSERT INTO player_profiles (id, club_id, team_id, first_name, last_name, linked_user_id, created_at)
SELECT gen_random_uuid(), t.club_id, pt.team_id,
       COALESCE(u.first_name, ''), COALESCE(u.last_name, ''),
       pt.player_id, CURRENT_TIMESTAMP
FROM player_teams pt
INNER JOIN teams t ON t.id = pt.team_id
INNER JOIN users u ON u.id = pt.player_id
WHERE t.club_id IS NOT NULL
AND NOT EXISTS (
    SELECT 1
    FROM player_teams pt2
    INNER JOIN teams t2 ON t2.id = pt2.team_id
    WHERE pt2.player_id = pt.player_id
    AND t2.club_id IS NOT NULL
    AND pt2.team_id < pt.team_id
)
AND NOT EXISTS (
    SELECT 1 FROM player_profiles existing WHERE existing.linked_user_id = pt.player_id
);
