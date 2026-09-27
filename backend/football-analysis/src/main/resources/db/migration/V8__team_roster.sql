CREATE TABLE IF NOT EXISTS team_roster (
    player_profile_id UUID NOT NULL,
    team_id UUID NOT NULL,
    PRIMARY KEY (player_profile_id, team_id),
    CONSTRAINT fk_team_roster_profile FOREIGN KEY (player_profile_id) REFERENCES player_profiles (id),
    CONSTRAINT fk_team_roster_team FOREIGN KEY (team_id) REFERENCES teams (id)
);

CREATE INDEX IF NOT EXISTS ix_team_roster_team_id ON team_roster (team_id);

INSERT INTO team_roster (player_profile_id, team_id)
SELECT id, team_id
FROM player_profiles
WHERE team_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM team_roster existing
      WHERE existing.player_profile_id = player_profiles.id
        AND existing.team_id = player_profiles.team_id
  );

ALTER TABLE player_profiles DROP COLUMN IF EXISTS team_id;
