ALTER TABLE team_invitations ADD COLUMN IF NOT EXISTS invite_kind VARCHAR(32);

UPDATE team_invitations
SET invite_kind = CASE invited_role
    WHEN 'PARENT' THEN 'PARENT'
    WHEN 'PLAYER' THEN 'PLAYER_SELF'
    WHEN 'COACH' THEN 'COACH'
    WHEN 'FAN' THEN 'FAN'
    ELSE invited_role
END
WHERE invite_kind IS NULL;

ALTER TABLE team_invitations ALTER COLUMN invite_kind SET NOT NULL;
