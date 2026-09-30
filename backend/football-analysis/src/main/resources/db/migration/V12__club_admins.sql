CREATE TABLE IF NOT EXISTS club_admins (
    user_id UUID NOT NULL,
    club_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, club_id),
    CONSTRAINT fk_club_admins_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_club_admins_club FOREIGN KEY (club_id) REFERENCES clubs (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_club_admins_club_id ON club_admins (club_id);
