CREATE INDEX IF NOT EXISTS ix_coach_teams_team_id_coach_id
    ON coach_teams (team_id, coach_id);

CREATE INDEX IF NOT EXISTS ix_fan_favorite_teams_team_id_fan_id
    ON fan_favorite_teams (team_id, fan_id);
