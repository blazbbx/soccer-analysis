CREATE TABLE IF NOT EXISTS club_admin_invites (
    id UUID PRIMARY KEY,
    club_id UUID NOT NULL,
    created_by_user_id UUID NOT NULL,
    token VARCHAR(64) NOT NULL,
    max_uses INTEGER NOT NULL,
    used_count INTEGER NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_club_admin_invites_token UNIQUE (token),
    CONSTRAINT fk_club_admin_invites_club FOREIGN KEY (club_id) REFERENCES clubs (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_club_admin_invites_club ON club_admin_invites (club_id);
