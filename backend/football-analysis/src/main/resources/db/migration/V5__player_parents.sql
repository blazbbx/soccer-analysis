CREATE TABLE IF NOT EXISTS player_parents (
    user_id UUID NOT NULL,
    player_profile_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, player_profile_id),
    CONSTRAINT fk_player_parents_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_player_parents_profile FOREIGN KEY (player_profile_id) REFERENCES player_profiles (id)
);

CREATE INDEX IF NOT EXISTS ix_player_parents_profile ON player_parents (player_profile_id);

ALTER TABLE team_invitations ADD COLUMN IF NOT EXISTS player_profile_id UUID;

ALTER TABLE team_invitations ADD CONSTRAINT fk_team_invitations_player_profile
    FOREIGN KEY (player_profile_id) REFERENCES player_profiles (id);
