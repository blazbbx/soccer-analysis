CREATE TABLE IF NOT EXISTS parent_invites (
    id UUID PRIMARY KEY,
    player_profile_id UUID NOT NULL,
    created_by_user_id UUID NOT NULL,
    token VARCHAR(64) NOT NULL,
    max_uses INTEGER NOT NULL,
    used_count INTEGER NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_parent_invites_token UNIQUE (token),
    CONSTRAINT fk_parent_invites_profile FOREIGN KEY (player_profile_id) REFERENCES player_profiles (id)
);

CREATE INDEX IF NOT EXISTS ix_parent_invites_profile ON parent_invites (player_profile_id);
