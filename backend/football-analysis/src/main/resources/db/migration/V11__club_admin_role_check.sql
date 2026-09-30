-- CLUB_ADMIN is a login role, same as PARENT. Hibernate does not rewrite
-- an existing check, so a JIT insert fails until this constraint is replaced.
-- team_invitations stays on the V7 list: a club admin is not invited onto a team.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('ADMIN', 'PLAYER', 'COACH', 'FAN', 'PARENT', 'CLUB_ADMIN'));

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_user_type_check;
ALTER TABLE users ADD CONSTRAINT users_user_type_check
    CHECK (user_type IN ('ADMIN', 'PLAYER', 'COACH', 'FAN', 'PARENT', 'CLUB_ADMIN'));
