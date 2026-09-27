-- Hibernate ddl-auto created users_role_check from the old UserRole enum
-- (ADMIN, PLAYER, COACH, FAN). PARENT was added later and Hibernate does not
-- rewrite existing check constraints, so JIT insert of a parent user fails.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('ADMIN', 'PLAYER', 'COACH', 'FAN', 'PARENT'));

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_user_type_check;
ALTER TABLE users ADD CONSTRAINT users_user_type_check
    CHECK (user_type IN ('ADMIN', 'PLAYER', 'COACH', 'FAN', 'PARENT'));

ALTER TABLE team_invitations DROP CONSTRAINT IF EXISTS team_invitations_invited_role_check;
ALTER TABLE team_invitations ADD CONSTRAINT team_invitations_invited_role_check
    CHECK (invited_role IN ('ADMIN', 'PLAYER', 'COACH', 'FAN', 'PARENT'));

ALTER TABLE team_invitations DROP CONSTRAINT IF EXISTS team_invitations_created_by_user_role_check;
ALTER TABLE team_invitations ADD CONSTRAINT team_invitations_created_by_user_role_check
    CHECK (created_by_user_role IN ('ADMIN', 'PLAYER', 'COACH', 'FAN', 'PARENT'));
