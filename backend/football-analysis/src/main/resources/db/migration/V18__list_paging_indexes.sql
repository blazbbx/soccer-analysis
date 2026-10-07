CREATE INDEX IF NOT EXISTS ix_matches_home_created
    ON matches (home_team_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS ix_matches_away_created
    ON matches (away_team_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS ix_matches_uploaded_by
    ON matches (uploaded_by_user_id);

CREATE INDEX IF NOT EXISTS ix_matches_created_at_id
    ON matches (created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS ix_clips_match_created
    ON clips (match_id, created_at DESC, id DESC);
