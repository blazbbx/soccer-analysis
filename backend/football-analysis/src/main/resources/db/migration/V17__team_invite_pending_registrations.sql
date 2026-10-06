ALTER TABLE team_invitations
    ADD COLUMN IF NOT EXISTS pending_registrations TEXT;
