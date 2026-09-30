ALTER TABLE teams ADD COLUMN IF NOT EXISTS sport varchar(100);

ALTER TABLE team_invitations DROP CONSTRAINT IF EXISTS team_invitations_created_by_user_role_check;
ALTER TABLE team_invitations ADD CONSTRAINT team_invitations_created_by_user_role_check
    CHECK (created_by_user_role IN ('ADMIN', 'PLAYER', 'COACH', 'FAN', 'PARENT', 'CLUB_ADMIN'));
